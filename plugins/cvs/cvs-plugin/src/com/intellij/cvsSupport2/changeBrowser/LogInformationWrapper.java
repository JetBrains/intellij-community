/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.command.log.Revision;
import org.netbeans.lib.cvsclient.command.log.SymbolicName;

import java.util.List;

public class LogInformationWrapper {
  private final String myFile;
  private final List<Revision> myRevisions;
  private final List<SymbolicName> mySymbolicNames;
  @NonNls private static final String CVS_REPOSITORY_FILE_POSTFIX = ",v";

  public LogInformationWrapper(final String file, final List<Revision> revisions, final List<SymbolicName> symbolicNames) {
    myFile = file;
    myRevisions = revisions;
    mySymbolicNames = symbolicNames;
  }

  public String getFile() {
    return myFile;
  }

  public List<Revision> getRevisions() {
    return myRevisions;
  }

  public List<SymbolicName> getSymbolicNames() {
    return mySymbolicNames;
  }

  @Nullable
  public static LogInformationWrapper wrap(final String repository, String module, final LogInformation log) {
    if (log.getRevisionList().isEmpty()) {
      return null;
    }
    final String rcsFileName = log.getRcsFileName();
    if (FileUtil.toSystemIndependentName(rcsFileName).startsWith(FileUtil.toSystemIndependentName(repository))) {
      return buildWrapper(log, rcsFileName, repository.length());
    }
    final int index = rcsFileName.indexOf(module); // hack
    if (index >= 0) {
      return  buildWrapper(log, rcsFileName, index);
    }
    return null;
  }

  private static LogInformationWrapper buildWrapper(LogInformation log, String rcsFileName, int length) {
    String relativePath = rcsFileName.substring(length);
    relativePath = StringUtil.trimStart(relativePath, "/");
    relativePath = StringUtil.trimEnd(relativePath, CVS_REPOSITORY_FILE_POSTFIX);
    return new LogInformationWrapper(relativePath, log.getRevisionList(), log.getAllSymbolicNames());
  }
}
