/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history;

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.persistent.SmallMapSerializer;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author irengrig
 */
public class NewGitUsersComponent {
  private final Object myLock = new Object();
  private SmallMapSerializer<String, List<String>> myState;
  private final File myFile;

  public NewGitUsersComponent(final Project project) {
    final File vcsFile = new File(PathManager.getSystemPath(), "vcs");
    File file = new File(vcsFile, "git_users_new");
    file.mkdirs();
    myFile = new File(file, project.getLocationHash());
  }

  public static NewGitUsersComponent getInstance(final Project project) {
    return PeriodicalTasksCloser.getInstance().safeGetService(project, NewGitUsersComponent.class);
  }

  @Nullable
  public List<String> get() {
    synchronized (myLock) {
      final List<String> data = myState.get("");
      return data == null ? null : Collections.unmodifiableList(data);
    }
  }

  public void acceptUpdate(@NotNull final Collection<String> data) {
    synchronized (myLock) {
      if (myState == null) return;
      final List<String> wasData = myState.get("");
      if (wasData == null || (! wasData.equals(data))) {
        final HashSet<String> set = new HashSet<String>(data);
        if (wasData != null) {
          set.addAll(wasData);
        }
        final List<String> list = new ArrayList<String>(set);
        Collections.sort(list);
        myState.put("", list);
        myState.force();
      }
    }
  }

  public void activate() {
    myState = new SmallMapSerializer<String, List<String>>(myFile, new EnumeratorStringDescriptor(), createExternalizer());
  }

  public void deactivate() {
    synchronized (myLock) {
      myState.force();
      myState = null;
    }
  }

  private static DataExternalizer<List<String>> createExternalizer() {
    return new MyDataExternalizer();
  }

  private static class MyDataExternalizer implements DataExternalizer<List<String>> {
    @Override
    public List<String> read(DataInput in) throws IOException {
      final int size = in.readInt();
      final ArrayList<String> result = new ArrayList<String>(size);
      for (int i = 0; i < size; i++) {
        result.add(in.readUTF());
      }
      return result;
    }

    @Override
    public void save(DataOutput out, List<String> value) throws IOException {
      out.writeInt(value.size());
      for (String s : value) {
        out.writeUTF(s);
      }
    }
  }
}
