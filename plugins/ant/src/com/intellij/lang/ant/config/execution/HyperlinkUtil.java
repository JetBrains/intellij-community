// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

final class HyperlinkUtil {
  public static final @NonNls String AT_ATR = "at";

  private HyperlinkUtil() {
  }

  static final class PlaceInfo {
    private final VirtualFile myFile;
    private final int myLine;
    private final int myColumn;
    private final int myLinkStartIndex;
    private final int myLinkEndIndex;

    PlaceInfo(VirtualFile file, int line, int column, int linkStartIndex, int linkEndIndex) {
      myFile = file;
      myLine = line;
      myColumn = column;
      myLinkStartIndex = linkStartIndex;
      myLinkEndIndex = linkEndIndex;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getLine() {
      return myLine;
    }

    public int getColumn() {
      return myColumn;
    }

    public int getLinkStartIndex() {
      return myLinkStartIndex;
    }

    public int getLinkEndIndex() {
      return myLinkEndIndex;
    }
  }

  static PlaceInfo parseStackLine(Project project, String line) {
    int atIndex = line.indexOf("\t"+ AT_ATR + " ");
    if (atIndex < 0) return null;
    int lparenthIndex = line.indexOf('(', atIndex);
    if (lparenthIndex < 0) return null;
    int lastDotIndex = line.lastIndexOf('.', lparenthIndex);
    if (lastDotIndex < 0 || lastDotIndex < atIndex) return null;
    String className = line.substring(atIndex + AT_ATR.length() + 1, lastDotIndex).trim();
    int dollarIndex = className.indexOf('$');
    if (dollarIndex >= 0){
      className = className.substring(0, dollarIndex);
    }
    int rparenthIndex = line.indexOf(')', lparenthIndex);
    if (rparenthIndex < 0) return null;
    String fileAndLine = line.substring(lparenthIndex + 1, rparenthIndex).trim();
    int colonIndex = fileAndLine.lastIndexOf(':');
    if (colonIndex < 0) return null;
    String lineString = fileAndLine.substring(colonIndex + 1);
    String file = fileAndLine.substring(0, colonIndex);
    int lineNumber;
    try{
      lineNumber = Integer.parseInt(lineString);
    }
    catch(NumberFormatException e){
      return null;
    }

    return makePlaceInfo(className, file, lineNumber, project, lparenthIndex, rparenthIndex);
  }

  private static PlaceInfo makePlaceInfo(final @NlsSafe String className,
                                         final @NlsSafe String fileName,
                                         final int line,
                                         final Project project,
                                         final int lparenthIndex,
                                         final int rparenthIndex) {
    final PlaceInfo[] info = new PlaceInfo[1];
    ApplicationManager.getApplication().runReadAction(
      () -> {
        PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (aClass == null) return;
        PsiFile file = aClass.getContainingFile();
        String fileName1 = fileName.replace(File.separatorChar, '/');
        int slashIndex = fileName1.lastIndexOf('/');
        String shortFileName = slashIndex < 0 ? fileName : fileName.substring(slashIndex + 1);
        final String name = file.getName();
        if (!name.equalsIgnoreCase(shortFileName)) return;
        info[0] = new PlaceInfo(file.getVirtualFile(), line, 1, lparenthIndex, rparenthIndex);
      }
    );
    return info[0];
  }


  private static final @NonNls String RUNNING_SUBSTRING = "Running ";
  private static final @NonNls String TEST_SUBSTRING = "TEST ";
  private static final @NonNls String FAILED_SUBSTRING = " FAILED";
  private static final @NonNls String FAILED_SUBSTRING_2 = " FAILED\n";

  static @Nullable PlaceInfo parseJUnitMessage(final Project project, String message) {
    int startIndex;
    int endIndex;
    if (message.startsWith(RUNNING_SUBSTRING)) {
      startIndex = RUNNING_SUBSTRING.length();
      endIndex = message.length();
    } else if (message.startsWith(TEST_SUBSTRING)) {
      startIndex = TEST_SUBSTRING.length();
      if (message.endsWith(FAILED_SUBSTRING)) {
        endIndex = message.length() - FAILED_SUBSTRING.length();
      } else if (message.endsWith(FAILED_SUBSTRING_2)) {
        endIndex = message.length() - FAILED_SUBSTRING_2.length();
      } else {
        return null;
      }
    } else {
      return null;
    }
    if (endIndex < startIndex) return null;
    final String possibleTestClassName = message.substring(startIndex, endIndex);

    final PsiFile[] psiFile = new PsiFile[1];

    final PsiManager psiManager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().runReadAction(() -> {
      PsiClass psiClass =
        JavaPsiFacade.getInstance(psiManager.getProject()).findClass(possibleTestClassName, GlobalSearchScope.allScope(project));
      if (psiClass == null) return;
      PsiElement parent = psiClass.getParent();
      if (parent instanceof PsiFile) {
        psiFile[0] = (PsiFile)parent;
      }
    });

    if (psiFile[0] == null) return null;
    return new PlaceInfo(psiFile[0].getVirtualFile(), 1, 1, startIndex, endIndex - 1);
  }

}

