// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.util.SimpleStringPattern;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * author: lesya
 */
public class IgnoredFilesInfoImpl implements IgnoredFilesInfo {

  private static final Logger LOG = Logger.getInstance(IgnoredFilesInfoImpl.class);

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
    @Override
    public boolean shouldBeIgnored(String fileName) {
      if (checkPatterns(CvsEntriesManager.getInstance().getUserDirIgnores().getPatterns(), fileName)) return true;
      if (checkPatterns(PREDEFINED_PATTERNS, fileName)) return true;
      return false;
    }

    @Override
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
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(cvsIgnoreFile), StandardCharsets.UTF_8));
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

  @Override
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
