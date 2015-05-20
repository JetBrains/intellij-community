/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.util.SimpleStringPattern;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class IgnoredFilesInfoImpl implements IgnoredFilesInfo {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl");

  private static final SimpleStringPattern[] PREDEFINED_PATTERNS = new SimpleStringPattern[]{
      new SimpleStringPattern("RCS"),
      new SimpleStringPattern("SCCS"),
      new SimpleStringPattern("CVS"),
      new SimpleStringPattern("CVS.adm"),
      new SimpleStringPattern("RCSLOG"),
      new SimpleStringPattern("cvslog.*"),
      new SimpleStringPattern("tags"),
      new SimpleStringPattern("TAGS"),
      new SimpleStringPattern(".make.state"),
      new SimpleStringPattern(".nse_depinfo"),
      new SimpleStringPattern("*~"),
      new SimpleStringPattern("#*"),
      new SimpleStringPattern(".#*"),
      new SimpleStringPattern(",*"),
      new SimpleStringPattern("_$*"),
      new SimpleStringPattern("*$"),
      new SimpleStringPattern("*.old"),
      new SimpleStringPattern("*.bak"),
      new SimpleStringPattern("*.BAK"),
      new SimpleStringPattern("*.orig"),
      new SimpleStringPattern("*.rej"),
      new SimpleStringPattern(".del-*"),
      new SimpleStringPattern("*.a"),
      new SimpleStringPattern("*.olb"),
      new SimpleStringPattern("*.o"),
      new SimpleStringPattern("*.obj"),
      new SimpleStringPattern("*.so"),
      new SimpleStringPattern("*.exe"),
      new SimpleStringPattern("*.Z"),
      new SimpleStringPattern("*.elc"),
      new SimpleStringPattern("*.ln"),
      new SimpleStringPattern("core")
  };

  public static final IgnoredFilesInfo EMPTY_FILTER = new IgnoredFilesInfoImpl(){
    public boolean shouldBeIgnored(String fileName) {
      if (checkPatterns(CvsEntriesManager.getInstance().getUserDirIgnores().getPatterns(), fileName)) return true;
      if (checkPatterns(PREDEFINED_PATTERNS, fileName)) return true;
      return false;
    }

    public boolean shouldBeIgnored(VirtualFile file) {
      final String fileName = file.getName();
      if (checkPatterns(CvsEntriesManager.getInstance().getUserDirIgnores().getPatterns(), fileName)) return true;
      if (file.isDirectory()) return false;
      return checkPatterns(PREDEFINED_PATTERNS, fileName);
    }
  };

  private List<SimpleStringPattern> myPatterns = null;

  public static IgnoredFilesInfo createForFile(File file){
    if (!file.isFile()) return EMPTY_FILTER;
    return new IgnoredFilesInfoImpl(file);
  }

  private IgnoredFilesInfoImpl() { }

  public static List<SimpleStringPattern> getPattensFor(File cvsIgnoreFile){
    ArrayList<SimpleStringPattern> result = new ArrayList();

    if (!cvsIgnoreFile.exists()) return result;

    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cvsIgnoreFile)));
      try{
      String line;
        while((line = reader.readLine()) != null){
          StringTokenizer stringTokenizer = new StringTokenizer(line, " ");
          while (stringTokenizer.hasMoreTokens()){
            result.add(new SimpleStringPattern(stringTokenizer.nextToken()));
          }

        }
      } catch (Exception ex){
        LOG.error(ex);
      } finally{
        try {
          reader.close();
        } catch (IOException e) {
          LOG.error(e);
        }
      }

    } catch (FileNotFoundException e) {
      LOG.error(e);
    }

    return result;
  }

  private IgnoredFilesInfoImpl(File cvsIgnoreFile){
    myPatterns = getPattensFor(cvsIgnoreFile);
  }

  public boolean shouldBeIgnored(String fileName) {
    if (EMPTY_FILTER.shouldBeIgnored(fileName)) return true;
    return checkPatterns(myPatterns, fileName);
  }

  @Override
  public boolean shouldBeIgnored(VirtualFile file) {
    if (EMPTY_FILTER.shouldBeIgnored(file)) return true;
    return checkPatterns(myPatterns, file.getName());
  }

  protected static boolean checkPatterns(List<SimpleStringPattern> patterns, String fileName) {
    for (int i = 0, patternsSize = patterns.size(); i < patternsSize; i++) {
      SimpleStringPattern simpleStringPattern = patterns.get(i);
      if (simpleStringPattern.doesMatch(fileName)) return true;
    }
    return false;
  }

  protected static boolean checkPatterns(SimpleStringPattern[] patterns, String fileName) {
    for (SimpleStringPattern pattern : patterns) {
      if (pattern.doesMatch(fileName)) return true;
    }
    return false;
  }

}
