package com.intellij.cvsSupport2.application;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.admin.Entries;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * author: lesya
 */
public class CvsInfo {

  public static CvsConnectionSettings ABSENT_SETTINGS = new MyInvalidCvsConnectionSettings();

  private IgnoredFilesInfo myIgnoreFilter;
  private CvsConnectionSettings myConnectionSettings;

  private String myRepository;
  private String myStickyTag;
  private Entries myEntries;

  private boolean myIsLoaded = false;

  private final VirtualFile myParent;
  private final CvsEntriesManager myCvsEntriesManager;
  private static final VirtualFile DUMMY_ROOT = null;

  public static final CvsInfo DUMMY = new CvsInfo(null, null) {
    public CvsConnectionSettings getConnectionSettings() {
      return ABSENT_SETTINGS;
    }

    public IgnoredFilesInfo getIgnoreFilter() {
      return IgnoredFilesInfoImpl.EMPTY_FILTER;
    }

    public Collection getEntries() {
      return new ArrayList();
    }

    public void clearFilter() {
    }

    public boolean isLoaded() {
      return true;
    }

    public Entry setEntryAndReturnReplacedEntry(Entry entry) {
      return null;
    }

    public void removeEntryNamed(String fileName) {
    }

    public VirtualFile getKey() {
      return null;
    }

    public Entry getEntryNamed(String name) {
      return null;
    }
  };
  private boolean myStickyTagIsLoaded = false;

  public CvsInfo(VirtualFile parent, CvsEntriesManager cvsEntriesManager) {
    myParent = parent;
    myCvsEntriesManager = cvsEntriesManager;
  }

  public CvsConnectionSettings getConnectionSettings() {
    checkIsLoaded();
    return myConnectionSettings;
  }

  private void checkIsLoaded() {

    if (myIsLoaded) return;
    try {
      loadCvsRoot();
      loadEntries();
      loadRepository();
      loadStikyTag();
      myCvsEntriesManager.watchForCvsAdminFiles(myParent);
    }
    finally {
      myIsLoaded = true;
    }
  }

  private void loadStikyTag() {
    myStickyTag = CvsUtil.loadStickyTagFrom(getParentFile());
  }

  private void loadRepository() {
    myRepository = CvsUtil.loadRepositoryFrom(getParentFile());
  }

  private void loadCvsRoot() {
    String cvsRoot = CvsUtil.loadRootFrom(getParentFile());
    try {
      if (cvsRoot == null) {
        myConnectionSettings = ABSENT_SETTINGS;
      }
      else {
        myConnectionSettings = myCvsEntriesManager.createConnectionSettingsOn(cvsRoot);
      }
    }
    catch (Exception ex) {
      myConnectionSettings = new MyInvalidCvsConnectionSettings();
    }
  }

  public IgnoredFilesInfo getIgnoreFilter() {
    checkFilterIsLoaded();
    return myIgnoreFilter;
  }

  private void checkFilterIsLoaded() {
    if (myIgnoreFilter == null) {
      if (myParent == null) {
        myIgnoreFilter = IgnoredFilesInfo.IGNORE_NOTHING;
      }
      else if (myCvsEntriesManager.fileIsIgnored(myParent)) {
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
    if (myParent != DUMMY_ROOT) {
      myEntries = createEntriesFor(getParentFile());
    }
    else {
      myEntries = new Entries();
    }
  }

  public Collection getEntries() {
    return getCvsEntries().getEntries();
  }

  private File getParentFile() {
    return CvsVfsUtil.getFileFor(myParent);
  }

  private Entries createEntriesFor(File parent) {
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

  public boolean isLoaded() {
    return myIsLoaded;
  }

  public Entry setEntryAndReturnReplacedEntry(Entry entry) {
    Entry previousEntry = getEntryNamed(entry.getFileName());
    appendEntry(entry);
    return previousEntry;
  }

  private void appendEntry(Entry newEntry) {
    getCvsEntries().addEntry(newEntry);
  }

  public void removeEntryNamed(String fileName) {
    removeEntry(fileName);
  }

  private void removeEntry(String fileName) {
    getCvsEntries().removeEntry(fileName);
  }

  public VirtualFile getKey() {
    return myParent;
  }

  public Entry getEntryNamed(String name) {
    return getCvsEntries().getEntry(name);
  }

  public String getRepository() {
    checkIsLoaded();
    return myRepository;
  }

  public String getStickyTag() {
    checkStickyTagIsLoaded();
    return myStickyTag;
  }

  private void checkStickyTagIsLoaded() {
    if (!myStickyTagIsLoaded){
      loadStikyTag();
      myStickyTagIsLoaded = true;
    }
  }

  public void cacheAll() {
    checkIsLoaded();
  }

  public void clearAll() {
    myEntries = null;
    myRepository = null;
    myStickyTagIsLoaded = false;
    myConnectionSettings = null;
    myIsLoaded = false;
  }

  public void clearStickyInformation() {
    myStickyTagIsLoaded = false;
  }

  private static class MyInvalidCvsConnectionSettings extends CvsConnectionSettings {
    public MyInvalidCvsConnectionSettings() {
      super(new CvsRootConfiguration(CvsApplicationLevelConfiguration.getInstance()));
    }

    public int getDefaultPort() {
      return 0;
    }

    public IConnection createConnection(ReadWriteStatistics statistics,
                                        ModalityContext executor) {
      throw new RuntimeException("createConnection() coult not be called (invalid cvs root)");
    }

    protected IConnection createOriginalConnection(ErrorRegistry errorRegistry,
                                                   ModalityContext executor,
                                                   CvsRootConfiguration cvsRootConfiguration) {
      throw new RuntimeException("createConnection() coult not be called (invalid cvs root)");
    }

    public CommandException processException(CommandException t) {
      return t;
    }

    public boolean login(ModalityContext executor) {
      Messages.showMessageDialog("Invalid cvs root: " + myStringRepsentation,
                                 "Cannot Connect to CVS",
                                 Messages.getErrorIcon());

      return false;
    }

    public boolean isValid() {
      return false;
    }
  }

}
