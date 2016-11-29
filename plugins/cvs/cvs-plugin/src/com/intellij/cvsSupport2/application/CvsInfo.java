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
package com.intellij.cvsSupport2.application;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorker;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entries;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * author: lesya
 */
public class CvsInfo {

  private volatile IgnoredFilesInfo myIgnoreFilter;
  private CvsConnectionSettings myConnectionSettings;

  private String myRepository;
  private String myStickyTag;
  private Entries myEntries;

  private boolean myIsLoaded = false;

  private final VirtualFile myParent;
  private static final VirtualFile DUMMY_ROOT = null;

  private boolean myStickyTagIsLoaded = false;

  public CvsInfo(VirtualFile parent) {
    myParent = parent;
  }

  public synchronized CvsConnectionSettings getConnectionSettings() {
    checkIsLoaded();
    return myConnectionSettings;
  }

  private void checkIsLoaded() {

    if (myIsLoaded) return;
    try {
      loadCvsRoot();
      loadEntries();
      loadRepository();
      loadStickyTag();

      if (myRepository != null && myConnectionSettings != null && myConnectionSettings.REPOSITORY != null) {
        myRepository = CvsUtil.getRelativeRepositoryPath(myRepository, myConnectionSettings.REPOSITORY);
      }

      CvsEntriesManager.getInstance().watchForCvsAdminFiles(myParent);
    }
    finally {
      myIsLoaded = true;
    }
  }

  private void loadStickyTag() {
    myStickyTag = CvsUtil.loadStickyTagFrom(getParentFile());
  }

  private void loadRepository() {
    myRepository = CvsUtil.loadRepositoryFrom(getParentFile());
  }

  private void loadCvsRoot() {
    String cvsRoot = CvsUtil.loadRootFrom(getParentFile());
    try {
      if (cvsRoot == null) {
        myConnectionSettings = getAbsentSettings();
      }
      else {
        myConnectionSettings = CvsEntriesManager.getInstance().createConnectionSettingsOn(cvsRoot);
      }
    }
    catch (Exception ex) {
      myConnectionSettings = getAbsentSettings();
    }
  }

  public synchronized IgnoredFilesInfo getIgnoreFilter() {
    checkFilterIsLoaded();
    return myIgnoreFilter;
  }

  private void checkFilterIsLoaded() {
    if (myIgnoreFilter == null) {
      if (myParent == null) {
        myIgnoreFilter = IgnoredFilesInfo.IGNORE_NOTHING;
      }
      else if (CvsEntriesManager.getInstance().fileIsIgnored(myParent)) {
        myIgnoreFilter = IgnoredFilesInfo.IGNORE_ALL;
      }
      else if (!CvsUtil.fileIsUnderCvs(myParent)) {
        myIgnoreFilter = IgnoredFilesInfo.IGNORE_NOTHING;
      }
      else {
        myIgnoreFilter = IgnoredFilesInfoImpl.createForFile(CvsUtil.cvsignoreFileFor(getParentFile()));
      }
    }
  }

  private Entries getCvsEntries() {
    checkIsLoaded();
    return myEntries;
  }

  private void loadEntries() {
    if (!Comparing.equal(myParent, DUMMY_ROOT)) {
      myEntries = createEntriesFor(getParentFile());
    }
    else {
      myEntries = new Entries();
    }
  }

  public synchronized Collection<Entry> getEntries() {
    return new HashSet<>(getCvsEntries().getEntries());
  }

  @Nullable
  private File getParentFile() {
    return myParent == null ? null : new File(myParent.getPath());
  }

  private static Entries createEntriesFor(File parent) {
    Entries entries = CvsUtil.getEntriesIn(new File(parent.getPath()));
    if (entries == null) {
      return new Entries();
    }
    else {
      return entries;
    }
  }


  public void clearFilter() {
    myIgnoreFilter = null;
  }

  public synchronized boolean isLoaded() {
    return myIsLoaded;
  }

  public synchronized Entry setEntryAndReturnReplacedEntry(Entry entry) {
    Entry previousEntry = getEntryNamed(entry.getFileName());
    appendEntry(entry);
    return previousEntry;
  }

  private void appendEntry(Entry newEntry) {
    getCvsEntries().addEntry(newEntry);
  }

  public synchronized void removeEntryNamed(String fileName) {
    removeEntry(fileName);
  }

  private void removeEntry(String fileName) {
    getCvsEntries().removeEntry(fileName);
  }

  public synchronized VirtualFile getKey() {
    return myParent;
  }

  public synchronized Entry getEntryNamed(String name) {
    return getCvsEntries().getEntry(name);
  }

  public synchronized String getRepository() {
    checkIsLoaded();
    return myRepository;
  }

  public synchronized String getStickyTag() {
    checkStickyTagIsLoaded();
    return myStickyTag;
  }

  private void checkStickyTagIsLoaded() {
    if (!myStickyTagIsLoaded){
      loadStickyTag();
      myStickyTagIsLoaded = true;
    }
  }

  public synchronized void cacheAll() {
    checkIsLoaded();
  }

  public synchronized void clearAll() {
    myEntries = null;
    myRepository = null;
    myStickyTagIsLoaded = false;
    myConnectionSettings = null;
    myIsLoaded = false;
  }

  public synchronized void clearStickyInformation() {
    myStickyTagIsLoaded = false;
  }

  private static class CvsConnectionSettingsHolder {
    private static final CvsConnectionSettings ABSENT_SETTINGS = new MyInvalidCvsConnectionSettings();
  }

  public static CvsConnectionSettings getAbsentSettings() {
    return CvsConnectionSettingsHolder.ABSENT_SETTINGS;
  }

  private static class CvsInfoHolder {
    private static final CvsInfo DUMMY = new DummyCvsInfo();
  }

  public static CvsInfo getDummyCvsInfo() {
    return CvsInfoHolder.DUMMY;
  }

  private static class MyInvalidCvsConnectionSettings extends CvsConnectionSettings {
    public MyInvalidCvsConnectionSettings() {
      super(CvsApplicationLevelConfiguration.createNewConfiguration(CvsApplicationLevelConfiguration.getInstance()));
    }

    @Override
    public int getDefaultPort() {
      return 0;
    }

    @Override
    public IConnection createConnection(ReadWriteStatistics statistics) {
      throw new RuntimeException(CvsBundle.message("exception.text.cannot.connect.with.invalid.root"));
    }

    @Override
    protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {
      throw new RuntimeException(CvsBundle.message("exception.text.cannot.connect.with.invalid.root"));
    }

    @Override
    public CommandException processException(CommandException t) {
      return t;
    }

    @Override
    public void setOffline(boolean offline) {
      throw new RuntimeException(CvsBundle.message("exception.text.cannot.do.setoffline.with.invalid.root"));
    }

    @Override
    public boolean isOffline() {
      return true;
    }

    @Override
    public CvsLoginWorker getLoginWorker(final Project project) {
      return new CvsLoginWorker() {
        @Override
        public boolean promptForPassword() {
          return true;
        }

        @Override
        public ThreeState silentLogin(boolean forceCheck) {
          VcsBalloonProblemNotifier.showOverChangesView(
            project, CvsBundle.message("message.error.invalid.cvs.root", getCvsRootAsString()), MessageType.ERROR);
          return ThreeState.NO;
        }

        @Override
        public void goOffline() {
          setOffline(true);
        }
      };
    }

    @Override
    public boolean isValid() {
      return false;
    }
  }

  @SuppressWarnings({"NonSynchronizedMethodOverridesSynchronizedMethod"})
  private static class DummyCvsInfo extends CvsInfo {
    public DummyCvsInfo() {
      super(null);
    }

    @Override
    public CvsConnectionSettings getConnectionSettings() {
      return getAbsentSettings();
    }

    @Override
    public IgnoredFilesInfo getIgnoreFilter() {
      return IgnoredFilesInfoImpl.EMPTY_FILTER;
    }

    @Override
    public Collection<Entry> getEntries() {
      return new ArrayList<>();
    }

    @Override
    public void clearFilter() {
    }

    @Override
    public boolean isLoaded() {
      return true;
    }

    @Override
    public Entry setEntryAndReturnReplacedEntry(Entry entry) {
      return null;
    }

    @Override
    public void removeEntryNamed(String fileName) {
    }

    @Override
    public VirtualFile getKey() {
      return null;
    }

    @Override
    public Entry getEntryNamed(String name) {
      return null;
    }
  }
}
