package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.cvsoperations.common.UpdatedFilesManager;
import com.intellij.cvsSupport2.javacvsImpl.ProjectContentInfoProvider;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.AdminWriter;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.file.*;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * author: lesya
 */
public class AdminWriterOnCache implements IAdminWriter {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls.AdminWriterOnCache");

  private final IAdminWriter myAdminWriter;
  private final CvsEntriesManager myCvsEntriesManager = CvsEntriesManager.getInstance();

  private final UpdatedFilesManager myUpdatedFilesManager;
  private final ProjectContentInfoProvider myProjectContentInfoProvider;

  public AdminWriterOnCache(UpdatedFilesManager updatedFilesManager,
                            ProjectContentInfoProvider projectContentInfoProvider) {
    myUpdatedFilesManager = updatedFilesManager;
    myProjectContentInfoProvider = projectContentInfoProvider;
    myAdminWriter = new AdminWriter(
      CodeStyleSettingsManager.getInstance().getCurrentSettings().getLineSeparator(),
      CvsApplicationLevelConfiguration.getCharset());    
  }

  public void ensureCvsDirectory(DirectoryObject directoryObject,
                                 String repositoryPath,
                                 CvsRoot cvsRoot,
                                 ICvsFileSystem cvsFileSystem) throws IOException {
    if (notUnderCvs(directoryObject, cvsFileSystem)) return;
    myAdminWriter.ensureCvsDirectory(directoryObject, repositoryPath, cvsRoot, cvsFileSystem);
    addDirectoryToParentEntriesFile(directoryObject, cvsFileSystem, cvsRoot.getCvsRoot());
  }

  private boolean notUnderCvs(AbstractFileObject directoryObject, ICvsFileSystem cvsFileSystem) {
    if (directoryObject == null) return false;
    final File directory = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
    VirtualFile virtualDirectory = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().findFileByIoFile(directory);
      }
    });
    if (virtualDirectory == null) return notUnderCvs(directoryObject.getParent(), cvsFileSystem);
    return !myProjectContentInfoProvider.fileIsUnderProject(virtualDirectory);

  }

  private void addDirectoryToParentEntriesFile(DirectoryObject directoryObject,
                                               ICvsFileSystem cvsFileSystem, String cvsRoot) throws IOException {
    DirectoryObject parentDirectoryObject = directoryObject.getParent();
    if (parentDirectoryObject == null) return;
    File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
    final File parentDirectory = directory.getParentFile();
    if (parentDirectory == null) return;
    VirtualFile virtualParent = CvsVfsUtil.findFileByIoFile(parentDirectory);
    if (virtualParent == null) return;
    String directoryName = directory.getName();
    Entry entry = myCvsEntriesManager.getEntryFor(virtualParent, directoryName);

    if (entry == null) {
      if (CvsUtil.fileIsUnderCvs(virtualParent)
          && CvsEntriesManager.getInstance().getCvsConnectionSettingsFor(virtualParent)
        .getCvsRootAsString().equals(cvsRoot)) {
        setEntry(parentDirectoryObject,
                 Entry.createDirectoryEntry(directoryName),
                 cvsFileSystem);
      }
    }
  }


  public void setEntry(final DirectoryObject directoryObject, final Entry entry, final ICvsFileSystem cvsFileSystem)
    throws IOException {
    File parent = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
    if (myUpdatedFilesManager.fileIsNotUpdated(new File(parent, entry.getFileName()))) return;
    if (notUnderCvs(directoryObject, cvsFileSystem)) return;
    myAdminWriter.setEntry(directoryObject, entry, cvsFileSystem);
    VirtualFile virtualParent = CvsVfsUtil.findFileByIoFile(parent);
    Entry existing = myCvsEntriesManager.getEntryFor(virtualParent, entry.getFileName());
    if (existing == null) myUpdatedFilesManager.addNewlyCreatedEntry(entry);
  }

  public void removeEntryForFile(final AbstractFileObject fileObject, final ICvsFileSystem cvsFileSystem)
    throws IOException {
    if (notUnderCvs(fileObject, cvsFileSystem)) return;
    File file = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
    if (myUpdatedFilesManager.fileIsNotUpdated(file)) return;
    myAdminWriter.removeEntryForFile(fileObject, cvsFileSystem);
  }

  public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
    LOG.assertTrue(false, "Cannot be called");
  }

  public void editFile(FileObject fileObject,
                       Entry entry,
                       ICvsFileSystem cvsFileSystem,
                       IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
    myAdminWriter.editFile(fileObject, entry, cvsFileSystem, fileReadOnlyHandler);
    File editBackupFile = getEditBackupFile(fileObject, cvsFileSystem);
    final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
    editBackupFile.setLastModified(file.lastModified());
  }

  public void uneditFile(FileObject fileObject,
                         ICvsFileSystem cvsFileSystem,
                         IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
    final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
    final File editBackupFile = getEditBackupFile(fileObject, cvsFileSystem);

    if (!editBackupFile.isFile()) {
      return;
    }

    FileUtil.copy(editBackupFile, file);

    file.setLastModified(editBackupFile.lastModified());
    myAdminWriter.uneditFile(fileObject, cvsFileSystem, fileReadOnlyHandler);
  }

  private File getEditBackupFile(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
    final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
    return new File(file.getParentFile(), CvsUtil.CVS + File.separatorChar + CvsUtil.BASE + File.separatorChar + file.getName());
  }


  public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem)
    throws IOException {
    myAdminWriter.setStickyTagForDirectory(directoryObject, tag, cvsFileSystem);
    File ioDirectory = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
    CvsEntriesManager.getInstance().getCvsInfoFor(CvsVfsUtil.findFileByIoFile(ioDirectory)).clearStickyInformation();
  }

  public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem)
    throws IOException {
    myAdminWriter.setEntriesDotStatic(directoryObject, set, cvsFileSystem);
  }

  public void writeTemplateFile(DirectoryObject directoryObject,
                                int fileLength,
                                InputStream inputStream,
                                IReaderFactory readerFactory,
                                IClientEnvironment clientEnvironment) throws IOException {
    LOG.assertTrue(directoryObject != null);
    myAdminWriter.writeTemplateFile(directoryObject, fileLength, inputStream, readerFactory, clientEnvironment);
  }

  public void directoryAdded(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
    LOG.assertTrue(directoryObject != null);
    LOG.assertTrue(directoryObject.getParent() != null, directoryObject.getPath());

    File directory = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
    VirtualFile virtualDirectory = LocalFileSystem.getInstance().findFileByIoFile(directory);
    if (virtualDirectory != null && !myProjectContentInfoProvider.fileIsUnderProject(virtualDirectory)) return;

    myAdminWriter.directoryAdded(directoryObject, cvsFileSystem);
    File ioDirectory = cvsFileSystem.getLocalFileSystem().getFile(directoryObject);
    CvsEntriesManager.getInstance().getCvsInfoFor(CvsVfsUtil.findFileByIoFile(ioDirectory)).clearAll();
  }
}
