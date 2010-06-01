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

import com.intellij.execution.process.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.*;
import com.intellij.vcsUtil.*;

import java.io.*;

import static org.testng.Assert.*;

/**
 * <strong><font color="#FF0000">TODO JavaDoc.</font></strong>
 */
public class AbstractHgTestCase extends AbstractVcsTestCase {
  protected void enableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    );
  }

  protected void disableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
    );
  }

  protected VirtualFile makeFile(File file) throws IOException {
    file.createNewFile();
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(myWorkingCopyDir);
    LocalFileSystem.getInstance().refresh(false);
    return VcsUtil.getVirtualFile(file);
  }

  protected ProcessOutput runHg(File aHgRepository, String... commandLine) throws IOException {
    String exe = SystemInfo.isWindows ? "hg.exe" : "hg";
    return runClient(exe, null, aHgRepository, commandLine);
  }

  protected File fillFile(File aParentDir, String[] filePath, String fileContents) throws FileNotFoundException {
    File parentDir = aParentDir;
    for (int i = 0; i < filePath.length - 1; i++) {
      File current = new File(parentDir, filePath[i]);
      if (!current.exists() || !current.isDirectory()) {
        assertTrue(current.mkdir());
      }
      parentDir = current;
    }
    File outputFile = new File(parentDir, filePath[filePath.length - 1]);

    PrintStream printer = new PrintStream(new FileOutputStream(outputFile));
    printer.print(fileContents);
    printer.close();

    return outputFile;
  }

  public static String added(String... path) {
    return "A " + path(path);
  }

  public static String removed(String... path) {
    return "R " + path(path);
  }

  public static String unknown(String... path) {
    return "? " + path(path);
  }

  public static String modified(String... path) {
    return "M " + path(path);
  }

  public static String path(String... line) {
    StringBuilder builder = new StringBuilder();

    int linePartCount = line.length;

    for (int i = 0; i < linePartCount; i++) {
      String linePart = line[i];
      builder.append(linePart);

      if (i < linePartCount - 1) {
        builder.append(File.separator);
      }
    }

    return builder.toString();
  }
}
