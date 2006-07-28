package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.admin.EntriesHandler;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IOFilesBasedDirectoryPruner {
  private final List<File> myFiles = new ArrayList<File>();
  private ProgressIndicator myProgressIndicator;
  private final String myCharset = CvsApplicationLevelConfiguration.getCharset();

  public IOFilesBasedDirectoryPruner(final ProgressIndicator progressIndicator) {
    myProgressIndicator = progressIndicator;
  }

  public void addFile(File file) {
    myFiles.add(file);
  }

  public void execute(){
    for (final File myFile : myFiles) {
      execute(myFile);
    }
  }

  private boolean execute(final File file) {
    if (file.isFile()) return false;

    if (myProgressIndicator != null) {
      myProgressIndicator.setText(CvsBundle.message("progress.text.prune.empty.directories"));
      myProgressIndicator.setText2(CvsBundle.message("progress.text.processing", file.getAbsolutePath()));
    }

    final File[] subFiles = file.listFiles();
    if (subFiles == null) return true;

    boolean canPrune = true;

    if (!new File(file, CvsUtil.CVS).isDirectory()) return false;
    for (File subFile : subFiles) {
      if (!isAdminDirectory(subFile)) {
        canPrune &= execute(subFile);
      }
    }

    canPrune &= !containsFileEntries(file);

    if (!canPrune) return false;

    if (!FileUtil.delete(file)) return false;

    CvsUtil.removeEntryFor(file);
    return true;
  }

  private boolean containsFileEntries(final File file) {
    final EntriesHandler entriesHandler = new EntriesHandler(file);
    try {
      entriesHandler.read(myCharset);
    }
    catch (IOException e) {
      return false;
    }
    final Collection entries = entriesHandler.getEntries().getEntries();
    for (final Object entry1 : entries) {
      Entry entry = (Entry)entry1;
      if (!entry.isDirectory()) return true;
    }
    return false;
  }

  private static boolean isAdminDirectory(final File file) {
    return file.isDirectory() && file.getName().equals(CvsUtil.CVS);
  }

}
