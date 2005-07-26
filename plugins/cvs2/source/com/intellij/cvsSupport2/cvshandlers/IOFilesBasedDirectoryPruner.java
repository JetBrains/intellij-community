package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
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
      myProgressIndicator.setText("Prune empty directories...");
      myProgressIndicator.setText2("Processing " +file.getAbsolutePath());
    }

    final File[] subFiles = file.listFiles();
    if (subFiles == null) return true;

    boolean canPrune = true;

    File adminDirectory = null;

    for (File subFile : subFiles) {
      if (isAdminDirectory(subFile)) {
        adminDirectory = subFile;
      }
      else {
        canPrune &= execute(subFile);
      }
    }

    if (adminDirectory == null) return false;

    canPrune &= !containsFileEntries(file);

    if (canPrune) {
      return FileUtil.delete(file);
    } else {
      return false;
    }


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

  private boolean isAdminDirectory(final File file) {
    return file.isDirectory() && file.getName().equals("CVS");
  }

}
