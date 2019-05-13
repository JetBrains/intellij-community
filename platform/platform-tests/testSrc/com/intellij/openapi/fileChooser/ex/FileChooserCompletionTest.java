package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FlyIdeaTestCase;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FileChooserCompletionTest extends FlyIdeaTestCase {

  private File myParent;
  private LocalFsFinder myFinder;

  private final Map<String, String> myMacros = new HashMap<>();
  private File myFolder11;
  private File myFolder21;

  private void basicSetup() throws IOException {
    myParent = getTempDir();

    myFolder11 = new File(myParent, "folder1/folder11");
    assertTrue(myFolder11.mkdirs());
    assertTrue(new File(myParent, "a").mkdirs());
    myFolder21 = new File(myParent, "folder1/folder11/folder21");
    assertTrue(myFolder21.mkdirs());
    assertTrue(new File(myParent, "folder1/folder12").mkdirs());
    assertTrue(new File(myParent, "file1").mkdir());
  }

  public void testBasicComplete() throws Exception {
    assertComplete("", ArrayUtil.EMPTY_STRING_ARRAY, null);
    assertComplete("1", ArrayUtil.EMPTY_STRING_ARRAY, null);


    basicSetup();

    assertComplete("f", ArrayUtil.EMPTY_STRING_ARRAY, null);

    assertComplete("/", new String[] {
      "a",
      "folder1",
      "file1"
    }, "a");

    assertComplete("/f", new String[] {
      "folder1",
      "file1"
    }, "file1");

    assertComplete("/fo", new String[] {
      "folder1",
    }, "folder1");

    assertComplete("/folder", new String[] {
      "folder1",
    }, "folder1");

    assertComplete("/folder1", new String[] {
      "folder1",
    }, "folder1");

    assertComplete("/folder1/", new String[] {
      "folder11",
      "folder12",
    }, "folder11");

    assertComplete("/folder1/folder1", new String[] {
      "folder11",
      "folder12",
    }, "folder11");

    assertComplete("/foo", ArrayUtil.EMPTY_STRING_ARRAY, null);

    assertTrue(new File(myParent, "qw/child.txt").mkdirs());
    assertTrue(new File(myParent, "qwe").mkdir());
    assertComplete("/qw", new String[] {
      "qw",
      "qwe"
    }, "qw");

    assertComplete("/qw/", new String[] {
      "child.txt"
    }, "child.txt");
  }

  public void testMiddleMatching() throws Exception {
    basicSetup();

    assertComplete("/old", new String[] {
      "folder1"
    }, "folder1");
  }

  public void testComplete() throws Exception {
    basicSetup();
    
    myParent = null;
    myMacros.put("$FOLDER_11$", myFolder11.getAbsolutePath());
    myMacros.put("$FOLDER_21$", myFolder21.getAbsolutePath());
    myMacros.put("$WHATEVER$", "/somepath");

    assertComplete("$", new String[] {"$FOLDER_11$", "$FOLDER_21$"}, "$FOLDER_11$");
  }

  private void assertComplete(String typed, String[] expected, String preselected) {
    myFinder = new LocalFsFinder() {
      @Override
      public LookupFile find(@NotNull final String path) {
        final File ioFile = new File(path);
        return ioFile.isAbsolute() ? new IoFile(ioFile) : null;
      }
    };

    String typedText = typed.replace("/", myFinder.getSeparator());

    final FileTextFieldImpl.CompletionResult result = new FileTextFieldImpl.CompletionResult();
    result.myCompletionBase = myParent != null ? (myParent.getAbsolutePath() + typedText) : typedText;

    new FileTextFieldImpl(new JTextField(), myFinder, new FileLookup.LookupFilter() {
      @Override
      public boolean isAccepted(final FileLookup.LookupFile file) {
        return true;
      }
    }, myMacros, getRootDisposable()) {
      @Override
      @Nullable
      public VirtualFile getSelectedFile() {
        return null;
      }
    }.processCompletion(result);

    for (int i = 0; i < expected.length; i++) {
      expected[i] = expected[i].replace("/", myFinder.getSeparator());
    }

    final List<String> expectedList = Arrays.asList(expected);

    Collections.sort(result.myToComplete, (o1, o2) -> o1.getName().compareTo(o2.getName()));
    Collections.sort(expectedList);

    assertEquals(asString(expectedList, result), asString(result.myToComplete, result));

    final String preselectedText = preselected != null ? preselected.replace("/", myFinder.getSeparator()) : null;
    assertEquals(preselectedText, toFileText(result.myPreselected, result));
    if (preselected != null) {
      assertTrue(result.myToComplete.contains(result.myPreselected));
    }
  }

  private String asString(List objects, FileTextFieldImpl.CompletionResult completion) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < objects.size(); i++) {
      final Object each = objects.get(i);
      result.append(toFileText(each, completion));
      if (i < objects.size() - 1) {
        result.append("\n");
      }
    }

    return result.toString();
  }

  private String toFileText(final Object each, final FileTextFieldImpl.CompletionResult completion) {
    String text = null;
    if (each instanceof FileLookup.LookupFile) {
      final FileLookup.LookupFile file = (FileLookup.LookupFile)each;
      if (file.getMacro() != null) {
        text = file.getMacro();
      } else {
        text = file.getName();
      }
    } else if (each != null) {
      text = each.toString();
    }

    if (text == null) return null;

    return (completion.myKidsAfterSeparator.contains(each) ? myFinder.getSeparator() : "" ) + text;
  }

}
