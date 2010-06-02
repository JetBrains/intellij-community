// Copyright 2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.openapi.application.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.*;
import com.intellij.vcsUtil.*;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * <strong><font color="#FF0000">TODO JavaDoc.</font></strong>
 */
public abstract class HgUtil {
  
  public static File copyResourceToTempFile(String basename, String extension) throws IOException {
    final InputStream in = HgUtil.class.getClassLoader().getResourceAsStream("python/" + basename + extension);

    final File tempFile = File.createTempFile(basename, extension);
    final byte[] buffer = new byte[4096];

    OutputStream out = null;
    try {
      out = new FileOutputStream(tempFile, false);
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1)
        out.write(buffer, 0, bytesRead);
    } finally {
      try {
        out.close();
      }
      catch (IOException e) {
        // ignore
      }
    }
    try {
      in.close();
    }
    catch (IOException e) {
      // ignore
    }
    tempFile.deleteOnExit();
    return tempFile;
  }

  public static void markDirectoryDirty( final Project project, final FilePath file ) {
    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
      }
    } );
    application.runWriteAction(new Runnable() {
      public void run() {
        VirtualFile virtualFile = VcsUtil.getVirtualFile(file.getPath());
        if (virtualFile != null) {
          virtualFile.refresh(true, true);
        }
      }
    } );
  }

  public static void markDirectoryDirty( final Project project, final VirtualFile file ) {
    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(file);
      }
    } );
    application.runWriteAction(new Runnable() {
      public void run() {
        file.refresh(true, true);
      }
    } );
  }

  public static void markFileDirty( final Project project, final FilePath file ) {
    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).fileDirty(file);
      }
    } );
    application.runWriteAction(new Runnable() {
      public void run() {
        VirtualFile virtualFile = VcsUtil.getVirtualFile(file.getPath());
        if (virtualFile != null) {
          virtualFile.refresh(true, false);
        }
      }
    } );
  }

  public static void markFileDirty( final Project project, final VirtualFile file ) {
    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        VcsDirtyScopeManager.getInstance(project).fileDirty(file);
      }
    } );
    application.runWriteAction(new Runnable() {
      public void run() {
        file.refresh(true, false);
      }
    } );
  }

  /**
   * Returns a temporary python file that will be deleted on exit.
   * 
   * Also all compiled version of the python file will be deleted.
   * 
   * @param base The basename of the file to copy
   * @return The temporary copy the specified python file, with all the necessary hooks installed
   * to make sure it is completely removed at shutdown
   */
  @Nullable
  public static File getTemporaryPythonFile(String base) {
    try {
      final File file = copyResourceToTempFile(base, ".py");
      final String fileName = file.getName();
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        public void run() {
          File[] files = file.getParentFile().listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
              return name.startsWith(fileName);
            }
          });
          for (File file1 : files) {
            file1.delete();
          }
        }
      });
      return file;
    } catch (IOException e) {
      return null;
    }
  }
}
