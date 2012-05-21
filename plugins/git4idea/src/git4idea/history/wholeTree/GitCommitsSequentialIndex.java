/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.persistent.SmallMapSerializer;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.ThrowableIterator;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import git4idea.GitRevisionNumber;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.SHAHash;

import java.io.*;
import java.util.*;

/**
 * !! application-level
 *
 * User: Irina.Chernushina
 * Date: 8/30/11
 * Time: 7:33 PM
 */
public class GitCommitsSequentialIndex implements GitCommitsSequentially {
  private final Object myLock;
  // to don't allow file reload while iterator is active
  private final File myListFile;

  // let it be simple
  private static final int ourInterval = 1000;
  private static final int ourRecordSize = 40 + 1 + 10 + 1;
  
  // start-to-now, time -> ascending. times at points each ourInterval commits written in file
  private final SLRUMap<VirtualFile, List<Long>> myPacks;
  // offset in file, root file
  private final SLRUMap<Pair<Long, VirtualFile>, List<Pair<AbstractHash, Long>>> myCache;
  private final File myDir;
  // loaded roots to files mapping
  private SmallMapSerializer<String, String> myState;
  private static final Logger LOG = Logger.getInstance("#git4idea.history.wholeTree.GitCommitsSequentialIndex");

  public GitCommitsSequentialIndex() {
    myLock = new Object();
    final File vcsFile = new File(PathManager.getSystemPath(), "vcs");
    myDir = new File(vcsFile, "git_line");
    myDir.mkdirs();
    // will contain list of roots mapped to
    myListFile = new File(myDir, "repository_index");
    myCache = new SLRUMap<Pair<Long, VirtualFile>, List<Pair<AbstractHash, Long>>>(10,10);
    myPacks = new SLRUMap<VirtualFile, List<Long>>(10,10);
  }

  public void activate() {
    synchronized (myLock) {
      if (myState == null) {
        myState = new SmallMapSerializer<String, String>(myListFile, new EnumeratorStringDescriptor(), createExternalizer());
      }
    }
  }

  public void deactivate() {
    synchronized (myLock) {
      if (myState == null) {
        LOG.info("Deactivate without activate");
        return;
      }
      myState.force();
      myState = null;
    }
  }
  
  private String getPutRootPath(final VirtualFile root) throws VcsException {
    synchronized (myLock) {
      String key = FilePathsHelper.convertPath(root);
      final String storedName = myState.get(key);
      if (storedName != null) return storedName;
      File tempFile = null;
      try {
        tempFile = File.createTempFile(root.getNameWithoutExtension(), ".dat", myDir);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
      String path = tempFile.getPath();
      myState.put(key, path);
      myState.force();
      return path;
    }
  }

  private DataExternalizer<String> createExternalizer() {
    return new DataExternalizer<String>() {
      @Override
      public void save(DataOutput out, String value) throws IOException {
        out.writeUTF(value);
      }

      @Override
      public String read(DataInput in) throws IOException {
        return in.readUTF();
      }
    };
  }

  // go back!!
  private class MyIterator implements ThrowableIterator<Pair<AbstractHash, Long>, VcsException> {
    private final VirtualFile myFile;
    private int myIdx;
    private final int myPacksSize;
    private Iterator<Pair<AbstractHash, Long>> myCurrent;

    private MyIterator(final VirtualFile file, final int idx, final int packsSize) throws VcsException {
      myFile = file;
      myIdx = idx;
      myPacksSize = packsSize;
      initIterator();
    }

    void initIterator() throws VcsException {
      final Pair<Long, VirtualFile> key = new Pair<Long, VirtualFile>((long) myIdx, myFile);
      List<Pair<AbstractHash, Long>> cached = myCache.get(key);
      if (cached == null) {
        cached = loadPack(myFile, myIdx);
        myCache.put(key, cached);
      }
      myCurrent = cached.iterator();
    }

    @Override
    public boolean hasNext() {
      return myCurrent.hasNext() || myIdx < myPacksSize;
    }

    @Override
    public Pair<AbstractHash, Long> next() throws VcsException {
      if (myCurrent.hasNext()) {
        return myCurrent.next();
      }
      ++ myIdx;
      initIterator();
      return myCurrent.next();
    }

    @Override
    public void remove() throws VcsException {
      throw new UnsupportedOperationException();
    }
  }

  private List<Long> getPacksWithLoad(VirtualFile file, String pathToFile) throws VcsException {
    List<Long> packs = myPacks.get(file);
    if (packs == null) {
      // reload
      packs = loadPacks(file, pathToFile);
    }
    return packs;
  }

  private int findLineTroughPacks(VirtualFile file, String pathToFile, final long ts) throws VcsException {
    synchronized (myLock) {
      List<Long> packs = getPacksWithLoad(file, pathToFile);
      if (packs == null) {
        return -1;
      }
      int found = Collections.binarySearch(packs, ts, Collections.<Long>reverseOrder());
      if (found >= 0) {
        // can find one of equal
        while (found > 0 && packs.get(found - 1) == ts) {
          -- found;
        }
        return found == 0 ? 0 : (found - 1);
      } else {
        int insertPlace = - found - 1;
        // todo possibly, if here we see that ts asked for is greater than what we cached, we can raise exception or reload some stuff
        return insertPlace == 0 ? 0 : (insertPlace - 1);
      }
    }
  }

  public static Pair<AbstractHash, Long> parseRecord(final String line) throws VcsException {
    int spaceIdx = line.indexOf(' ');
    // todo concern index file deletion and re-ask
    if (spaceIdx == -1) throw new VcsException("Can not parse written index file");
    try {
      long next = Long.parseLong(line.substring(spaceIdx + 1));
      return new Pair<AbstractHash, Long>(AbstractHash.create(line.substring(0, spaceIdx)), next * 1000);
      // todo concern index file deletion and re-ask
    } catch (NumberFormatException e) {
      throw new VcsException(e);
    }
  }
  
  private List<Pair<AbstractHash, Long>> loadPack(final VirtualFile file, final long packNumber) throws VcsException {
    final ArrayList<Pair<AbstractHash, Long>> data = new ArrayList<Pair<AbstractHash, Long>>();
    synchronized (myLock) {
      String key = FilePathsHelper.convertPath(file);
      String outFileName = myState.get(key);
      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(outFileName, "r");
        long len = raf.length();
        long offset = len - packNumber * ourInterval * ourRecordSize;

        long recordsInPiece = offset / ourRecordSize;
        long size = recordsInPiece >= ourInterval ? ourInterval : recordsInPiece;
        ((ArrayList) data).ensureCapacity((int) size);

        raf.seek(offset - size * ourRecordSize);
        for (int i = 0; i < size && i < len/ourRecordSize; i++) {
          String line = raf.readLine();
          data.add(parseRecord(line));
        }
      }
      catch (FileNotFoundException e) {
        throw new VcsException(e);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
      finally {
        try {
          if (raf != null) {
            raf.close();
          }
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
      }
      Collections.reverse(data);
      myCache.put(new Pair<Long, VirtualFile>(packNumber, file), data);
    }
    return data;
  }

  private ArrayList<Long> loadPacks(VirtualFile file, String pathToFile) throws VcsException {
    synchronized (myLock) {
      final ArrayList<Long> packs = new ArrayList<Long>();
      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(pathToFile, "r");
        long len = raf.length();
        ((ArrayList) packs).ensureCapacity((int)(len/(ourRecordSize * ourInterval)) + 1);

        for (long i = (len - ourRecordSize); i >= 0; i-= (ourRecordSize * ourInterval)) {
          raf.seek(i);
          final String line = raf.readLine();
          packs.add(parseRecord(line).getSecond());
        }
      }
      catch (FileNotFoundException e) {
        throw new VcsException(e);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
      finally {
        try {
          if (raf != null) {
            raf.close();
          }
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
      }

      myPacks.put(file, packs);
      return packs;
    }
  }

  @Override
  public void iterateDescending(VirtualFile file,
                                long commitTime,
                                Processor<Pair<AbstractHash, Long>> consumer) throws VcsException {
    final String key = FilePathsHelper.convertPath(file);
    synchronized (myLock) {
      String pathToFile = myState.get(key);
      if (pathToFile == null || ! new File(pathToFile).exists()) return;
      int idx;
      if (commitTime == -1) {
        idx = 0;
      } else {
        idx = findLineTroughPacks(file, pathToFile, commitTime);
        if (idx == -1) return;
      }

      List<Long> packs = getPacksWithLoad(file, pathToFile);
      final MyIterator iterator = new MyIterator(file, idx, packs.size());
      if (commitTime != -1) {
        while (iterator.hasNext()) {
          final Pair<AbstractHash, Long> next = iterator.next();
          if (next.getSecond() <= commitTime) {
            if (! consumer.process(next)) {
              return;
            }
            break;
          }
        }
      }
      while (iterator.hasNext()) {
        final Pair<AbstractHash, Long> next = iterator.next();
        if (! consumer.process(next)) break;
      }
    }
  }

  @Override
  public void pushUpdate(final Project project, final VirtualFile file, final ContinuationContext context) {
    context.next(new LoadTask(file, project));
  }

  private class LoadTask extends TaskDescriptor {
    private final Project myProject;
    private final VirtualFile myFile;

    private LoadTask(VirtualFile file, Project project) {
      super("Refresh repository " + file.getPath() + " cache", Where.POOLED);
      myFile = file;
      myProject = project;
    }

    @Override
    public void run(ContinuationContext context) {
      // we need a lock here to prevent parallel access to file from our application
      // (generally possible)
      // todo consider lock on file name
      synchronized (myLock) {
        try {
          loadImpl();
        }
        catch (VcsException e) {
          context.cancelEverything();
          if (! context.handleException(e, false)) {
            VcsBalloonProblemNotifier.showOverChangesView(myProject, e.getMessage(), MessageType.ERROR);
            // and exit, do not ping
          }
        }
      }
    }

    private void loadImpl() throws VcsException {
      final AbstractHash[] latestWrittenHash = new AbstractHash[1];
      final Long[] latestWrittenTime = new Long[1];
      iterateDescending(myFile, -1, new Processor<Pair<AbstractHash, Long>>() {
        @Override
        public boolean process(Pair<AbstractHash, Long> abstractHashLongPair) {
          latestWrittenHash[0] = abstractHashLongPair.getFirst();
          latestWrittenTime[0] = abstractHashLongPair.getSecond();
          return false;
        }
      });
      if (latestWrittenHash[0] != null) {
        ItemLatestState lastRevision = GitHistoryUtils.getLastRevision(myProject, new FilePathImpl(myFile));
        if (lastRevision == null) {
          // no history at the moment
          return;
        }
        if (lastRevision.isItemExists() && ((GitRevisionNumber) lastRevision.getNumber()).getRev().equals(latestWrittenHash[0].getString())) {
          // no refresh needed
          return;
        }
        appendHistory(latestWrittenTime[0], latestWrittenHash[0]);
      } else {
        initHistory();
      }
    }

    private void initHistory() throws VcsException {
      final String outFilePath = getPutRootPath(myFile);
      GitHistoryUtils.dumpFullHistory(myProject, myFile, outFilePath);
    }

    private void appendHistory(final long since, final AbstractHash hash) throws VcsException {
      final String outFilePath = getPutRootPath(myFile);

      final List<Pair<SHAHash,Date>> pairs =
              GitHistoryUtils.onlyHashesHistory(myProject, new FilePathImpl(myFile), "--all", "--date-order", "--full-history", "--sparse",
                                                "--after=" + (since/1000));
      if (pairs.isEmpty()) return;
      final String startAsString = hash.getString();

      Iterator<Pair<SHAHash, Date>> iterator = pairs.iterator();
      while (iterator.hasNext()) {
        Pair<SHAHash, Date> next = iterator.next();
        if (next.getFirst().getValue().equals(startAsString)) {
          break;
        }
      }

      OutputStream stream = null;
      try {
        stream = new BufferedOutputStream(new FileOutputStream(new File(outFilePath), true));
        while (iterator.hasNext()) {
          Pair<SHAHash, Date> next = iterator.next();
          stream.write(new StringBuilder().append(next.getFirst().getValue()).append(" ").
                  append(next.getSecond().getTime()).append('\n').toString().getBytes(CharsetToolkit.UTF8_CHARSET));
        }
      }
      catch (FileNotFoundException e) {
        throw new VcsException(e);
      }
      catch (IOException e) {
        throw new VcsException(e);
      }
      finally {
        try {
          if (stream != null) {
            stream.close();
          }
        }
        catch (IOException e) {
          throw new VcsException(e);
        }
      }
    }
  }
}
