/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history;

import com.intellij.lifecycle.AtomicSectionsAware;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UpdatedReference;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vcs.changes.ControlledCycle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.PersistentHashMap;
import com.intellij.util.io.storage.HeavyProcessLatch;
import git4idea.GitVcs;
import git4idea.history.browser.ChangesFilter;
import git4idea.history.browser.GitCommit;
import git4idea.history.browser.LowLevelAccess;
import git4idea.history.browser.LowLevelAccessImpl;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class GitUsersComponent {
  private final static int ourPackSize = 50;
  private static final int ourBackInterval = 60 * 1000;
  private static final int ourForwardInterval = 100 * 60 * 1000;
  
  private final static Logger LOG = Logger.getInstance("#git4idea.history.GitUsersComponent");

  private final Object myLock;
  private PersistentHashMap<String, UsersData> myState;
  private final Map<VirtualFile, Pair<String, LowLevelAccess>> myAccessMap;

  // activate-deactivate
  private volatile boolean myIsActive;
  private ControlledCycle myControlledCycle;
  private VcsListener myVcsListener;
  private final GitVcs myVcs;
  private final ProjectLevelVcsManager myManager;
  private final File myFile;

  public GitUsersComponent(final ProjectLevelVcsManager manager) {
    myVcs = (GitVcs) manager.findVcsByName(GitVcs.getKey().getName());
    myManager = manager;
    myLock = new Object();

    final File vcsFile = new File(PathManager.getSystemPath(), "vcs");
    File file = new File(vcsFile, "git_users");
    file.mkdirs();
    myFile = new File(file, myVcs.getProject().getLocationHash());

    myAccessMap = new HashMap<VirtualFile, Pair<String, LowLevelAccess>>();

    // every 10 seconds is ok to check
    myControlledCycle = new ControlledCycle(myVcs.getProject(), new MyRefresher(), "Git users loader");
    myVcsListener = new VcsListener() {
      public void directoryMappingChanged() {
        final VirtualFile[] currentRoots = myManager.getRootsUnderVcs(myVcs);
        synchronized (myLock) {
          myAccessMap.clear();
          for (VirtualFile currentRoot : currentRoots) {
            myAccessMap.put(currentRoot, new Pair<String, LowLevelAccess>(currentRoot.getPath(),
                                                                          new LowLevelAccessImpl(myVcs.getProject(), currentRoot)));
          }
        }
      }
    };
  }

  public static GitUsersComponent getInstance(final Project project) {
    return ServiceManager.getService(project, GitUsersComponent.class);
  }

  @Nullable
  public List<String> getUsersList(final VirtualFile root) {
    final Pair<String, LowLevelAccess> pair = myAccessMap.get(root);
    if (pair == null) return null;
    try {
      return new ArrayList<String>(myState.get(pair.getFirst()).getUsers());
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  // singleton update process, only roots can change outside
  private class MyRefresher implements ControlledCycle.MyCallback {
    public boolean call(final AtomicSectionsAware atomicSectionsAware) {
      atomicSectionsAware.checkShouldExit();
      if (myIsActive) {
        try {
          final HashMap<VirtualFile, Pair<String, LowLevelAccess>> copy;
          synchronized (myLock) {
            copy = new HashMap<VirtualFile, Pair<String, LowLevelAccess>>(myAccessMap);
          }

          final Map<String, UsersData> toUpdate = new HashMap<String, UsersData>();
          for (Pair<String, LowLevelAccess> pair : copy.values()) {
            atomicSectionsAware.checkShouldExit();

            final String key = pair.getFirst();
            UsersData data = myState.get(key);
            if (data == null) {
              data = new UsersData();
              data.forceUpdate();
            }
            if (data.load(pair.getSecond(), atomicSectionsAware)) {
              toUpdate.put(key, data);
            }
          }

          for (String s : toUpdate.keySet()) {
            myState.put(s, toUpdate.get(s));
          }
          if (! toUpdate.isEmpty()) {
            myState.force();
          }
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
      return myIsActive;
    }
  }

  public void activate() {
    try {
      myState = new PersistentHashMap<String, UsersData>(myFile, new EnumeratorStringDescriptor(), createExternalizer());
    }
    catch (IOException e) {
      myState = null;
    }

    myIsActive = true;
    myManager.addVcsListener(myVcsListener);
    myControlledCycle.start();
  }

  public void deactivate() {
    if (myState != null) try {
      myState.close();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    myState = null;

    synchronized (myLock) {
      myAccessMap.clear();
    }
    myManager.removeVcsListener(myVcsListener);
    myIsActive = false;
  }

  private DataExternalizer<UsersData> createExternalizer() {
    return new DataExternalizer<UsersData>() {
      public void save(DataOutput out, UsersData value) throws IOException {
        final UpdatedReference<Long> closer = value.getCloserDate();
        out.writeLong(closer.getT());
        out.writeLong(closer.getTime());

        final UpdatedReference<Long> earlier = value.getEarlierDate();
        out.writeLong(earlier.getT());
        out.writeLong(earlier.getTime());

        final List<String> users = value.getUsers();
        out.writeInt(users.size());
        for (String user : users) {
          out.writeUTF(user);
        }

        out.writeBoolean(value.isStartReached());
      }

      public UsersData read(DataInput in) throws IOException {
        final UsersData data = new UsersData();
        final long closerDate = in.readLong();
        final long closerUpdate = in.readLong();
        data.setCloserDate(new UpdatedReference<Long>(closerDate, closerUpdate));
        final long earlierDate = in.readLong();
        final long earlierUpdate = in.readLong();
        data.setEarlierDate(new UpdatedReference<Long>(earlierDate, earlierUpdate));

        final List<String> users = new LinkedList<String>();
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
          users.add(in.readUTF());
        }
        data.addUsers(users);
        data.setStartReached(in.readBoolean());
        return data;
      }
    };
  }

  private class UsersData {
    private UpdatedReference<Long> myCloserDate;
    private UpdatedReference<Long> myEarlierDate;
    private final List<String> myUsers;
    private boolean myForceUpdate;
    private boolean myStartReached;

    private UsersData() {
      myUsers = new LinkedList<String>();
      final long now = System.currentTimeMillis();
      myCloserDate = new UpdatedReference<Long>(now);
      myEarlierDate = new UpdatedReference<Long>(now + 1);
    }

    public UpdatedReference<Long> getCloserDate() {
      return myCloserDate;
    }

    public void setCloserDate(UpdatedReference<Long> closerDate) {
      myCloserDate = closerDate;
    }

    public UpdatedReference<Long> getEarlierDate() {
      return myEarlierDate;
    }

    public void setEarlierDate(UpdatedReference<Long> earlierDate) {
      myEarlierDate = earlierDate;
    }

    public void forceUpdate() {
      myForceUpdate = true;
    }

    public List<String> getUsers() {
      return myUsers;
    }

    public boolean isStartReached() {
      return myStartReached;
    }

    public void setStartReached(boolean startReached) {
      myStartReached = startReached;
    }

    public void addUsers(final Collection<String> users) {
      myUsers.addAll(users);
    }

    public boolean load(final LowLevelAccess lowLevelAccess, final AtomicSectionsAware atomicSectionsAware) {
      if (HeavyProcessLatch.INSTANCE.isRunning()) return false;

      HeavyProcessLatch.INSTANCE.processStarted();
      try {
        final Set<String> newData = new HashSet<String>();
        boolean result = false;
        if ((! myStartReached) && (myForceUpdate || myEarlierDate.isTimeToUpdate(ourBackInterval))) {
          result = true;
          lookBack(lowLevelAccess, newData, atomicSectionsAware);
        }
        if (myForceUpdate || myCloserDate.isTimeToUpdate(ourForwardInterval)) {
          result = true;
          lookForward(lowLevelAccess, newData, atomicSectionsAware);
        }
        myForceUpdate = false;
        
        result |= ! newData.isEmpty();
        putNewData(newData);
        return result;
      } finally {
        HeavyProcessLatch.INSTANCE.processFinished();
      }
    }

    private void putNewData(Set<String> newData) {
      newData.addAll(myUsers);
      myUsers.clear();
      myUsers.addAll(newData);
      Collections.sort(myUsers);
    }

    private void lookForward(LowLevelAccess lowLevelAccess, Set<String> newData, AtomicSectionsAware atomicSectionsAware) {
      myCloserDate.updateTs();
      loadImpl(lowLevelAccess, newData, null, new Date(myCloserDate.getT()), atomicSectionsAware);
    }

    private void lookBack(LowLevelAccess lowLevelAccess, Set<String> newData, AtomicSectionsAware atomicSectionsAware) {
      myEarlierDate.updateTs();
      loadImpl(lowLevelAccess, newData, new Date(myEarlierDate.getT()), null, atomicSectionsAware);
    }

    private void loadImpl(LowLevelAccess lowLevelAccess,
                          final Set<String> newData,
                          @Nullable final Date before,
                          @Nullable final Date after,
                          final AtomicSectionsAware atomicSectionsAware) {
      try {
        final Ref<Long> beforeTick = new Ref<Long>(Long.MAX_VALUE); // min
        final Ref<Long> afterTick = new Ref<Long>(-1L);  // max
        lowLevelAccess.loadCommits(Collections.<String>emptyList(), before, after, Collections.<ChangesFilter.Filter>emptyList(),
                                   new Consumer<GitCommit>() {
                                     public void consume(GitCommit gitCommit) {
                                       atomicSectionsAware.checkShouldExit();

                                       final long time = gitCommit.getDate().getTime();
                                       beforeTick.set(Math.min(beforeTick.get(), time));
                                       afterTick.set(Math.max(afterTick.get(), time));

                                       if (! StringUtil.isEmptyOrSpaces(gitCommit.getAuthor())) {
                                        newData.add(gitCommit.getAuthor());
                                       }
                                       if (! StringUtil.isEmptyOrSpaces(gitCommit.getCommitter())) {
                                        newData.add(gitCommit.getCommitter());
                                       }
                                       if (gitCommit.getParentsHashes().isEmpty()) {
                                         myStartReached = true;
                                       }
                                     }
                                   }, ourPackSize, Collections.<String>emptyList());
        if (myCloserDate.getT() < afterTick.get()) {
          myCloserDate.updateT(afterTick.get());
        }
        if (myEarlierDate.getT() > beforeTick.get()) {
          myEarlierDate.updateT(beforeTick.get());
        }
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
  }
}
