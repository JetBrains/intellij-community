/*****************************************************************************
 * Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License Version
 * 1.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is available at http://www.sun.com/
 *
 * The Original Code is the CVS Client Library.
 * The Initial Developer of the Original Code is Robert Greig.
 * Portions created by Robert Greig are Copyright (C) 2000.
 * All Rights Reserved.
 *
 * Contributor(s): Robert Greig.
 *****************************************************************************/
package org.netbeans.lib.cvsclient.response;

import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import org.netbeans.lib.cvsclient.admin.Entry;
import org.netbeans.lib.cvsclient.admin.IAdminWriter;
import org.netbeans.lib.cvsclient.command.update.UpdatedFileInfo;
import org.netbeans.lib.cvsclient.file.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @author Thomas Singer
 */
public final class DefaultResponseHandler extends AbstractResponseHandler {

  // Implemented ============================================================

  @Override
  public void processErrorMessageResponse(byte[] message, IResponseServices responseServices) {
    responseServices.getEventSender().notifyMessageListeners(message, true, false);
  }

  @Override
  public void processMessageResponse(byte[] message, IResponseServices responseServices) {
    responseServices.getEventSender().notifyMessageListeners(message, false, false);
  }

  @Override
  public void processMessageTaggedResponse(byte[] message, IResponseServices responseServices) {
    responseServices.getEventSender().notifyMessageListeners(message, false, true);
  }

  @Override
  public void processBinaryMessageResponse(final int fileLength, final byte[] binaryContent, IResponseServices responseServices) {
    responseServices.getEventSender().notifyFileInfoListeners(binaryContent);
  }

  @Override
  public void processCheckedInResponse(String relativeLocalDirectory,
                                       String repositoryFilePath,
                                       String entryLine,
                                       IResponseServices responseServices,
                                       IClientEnvironment clientEnvironment) throws IOException {
    final FileObject fileObject = clientEnvironment.getCvsFileSystem().getFileObject(relativeLocalDirectory, repositoryFilePath);
    final Entry entry = Entry.createEntryForLine(entryLine);

    // for added and removed entries set the conflict to Dummy timestamp.
    if (entry.isAddedFile() || entry.isRemoved()) {
      entry.setDummyTimestamp();
    }
    else {
      final File file = clientEnvironment.getCvsFileSystem().getLocalFileSystem().getFile(fileObject);
      final String mode = responseServices.getNextFileMode();
      final boolean readOnly = mode != null && FileUtils.isReadOnlyMode(mode);
      clientEnvironment.getFileReadOnlyHandler().setFileReadOnly(file, readOnly);

      final Date date = new Date(file.lastModified());
      entry.parseConflictString(Entry.getLastModifiedDateFormatter().format(date));
    }

    clientEnvironment.getAdminWriter().setEntry(fileObject.getParent(), entry, clientEnvironment.getCvsFileSystem());
    responseServices.getEventSender().notifyEntryListeners(fileObject, entry);
  }

  @Override
  public void processNewEntryResponse(String relativeLocalDirectory,
                                      String repositoryFilePath,
                                      IResponseServices responseServices,
                                      String entryLine,
                                      IClientEnvironment clientEnvironment) throws IOException {
    // we set the date the file was last modified in the Entry line
    // so that we can easily determine whether the file has been
    // untouched
    final FileObject fileObject = clientEnvironment.getCvsFileSystem().getFileObject(relativeLocalDirectory, repositoryFilePath);
    final Entry entry = Entry.createEntryForLine(entryLine);
    entry.setDummyTimestamp();

    clientEnvironment.getAdminWriter().setEntry(fileObject.getParent(), entry, clientEnvironment.getCvsFileSystem());
    responseServices.getEventSender().notifyEntryListeners(fileObject, entry);
  }

  @Override
  public void processSetStaticDirectoryResponse(String relativeLocalDirectory,
                                                String repositoryFilePath,
                                                IResponseServices responseServices,
                                                IClientEnvironment clientEnvironment) throws IOException {
    final ICvsFileSystem cvsFileSystem = clientEnvironment.getCvsFileSystem();
    final DirectoryObject directoryObject = cvsFileSystem.getDirectoryObject(relativeLocalDirectory, repositoryFilePath);

    final IAdminWriter adminWriter = clientEnvironment.getAdminWriter();
    adminWriter.ensureCvsDirectory(directoryObject, repositoryFilePath, clientEnvironment.getCvsRoot(), cvsFileSystem);
    adminWriter.setEntriesDotStatic(directoryObject, true, cvsFileSystem);

    responseServices.getEventSender().notifyDirectoryListeners(directoryObject, true);
  }

  @Override
  public void processClearStaticDirectoryResponse(String relativeLocalDirectory,
                                                  String repositoryDirectoryPath,
                                                  IResponseServices responseServices,
                                                  IClientEnvironment clientEnvironment) throws IOException {
    final ICvsFileSystem cvsFileSystem = clientEnvironment.getCvsFileSystem();
    final DirectoryObject directoryObject = cvsFileSystem.getDirectoryObject(relativeLocalDirectory, repositoryDirectoryPath);

    final IAdminWriter adminWriter = clientEnvironment.getAdminWriter();
    adminWriter.ensureCvsDirectory(directoryObject, repositoryDirectoryPath, clientEnvironment.getCvsRoot(), cvsFileSystem);
    adminWriter.setEntriesDotStatic(directoryObject, false, cvsFileSystem);

    responseServices.getEventSender().notifyDirectoryListeners(directoryObject, false);
  }

  @Override
  public void processSetStickyResponse(String relativeLocalDirectory,
                                       String repositoryFilePath,
                                       String tag,
                                       IClientEnvironment clientEnvironment) throws IOException {
    final DirectoryObject directoryObject =
      clientEnvironment.getCvsFileSystem().getDirectoryObject(relativeLocalDirectory, repositoryFilePath);
    clientEnvironment.getAdminWriter().setStickyTagForDirectory(directoryObject, tag, clientEnvironment.getCvsFileSystem());
  }

  @Override
  public void processClearStickyResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment)
    throws IOException {
    final ICvsFileSystem cvsFileSystem = clientEnvironment.getCvsFileSystem();
    final DirectoryObject directoryObject = cvsFileSystem.getDirectoryObject(relativeLocalDirectory, repositoryFilePath);

    final IAdminWriter adminWriter = clientEnvironment.getAdminWriter();
    adminWriter.ensureCvsDirectory(directoryObject, repositoryFilePath, clientEnvironment.getCvsRoot(), cvsFileSystem);
    adminWriter.setStickyTagForDirectory(directoryObject, null, cvsFileSystem);
  }

  @Override
  public void processNotifiedResponse(String relativeLocalDirectory, String repositoryFilePath, IClientEnvironment clientEnvironment) {
  }

  @Override
  public void processRemovedResponse(String relativeLocalDirectory,
                                     String repositoryFilePath,
                                     IResponseServices responseServices,
                                     IClientEnvironment clientEnvironment) throws IOException {
    final ICvsFileSystem fileSystem = clientEnvironment.getCvsFileSystem();
    final FileObject fileObject = fileSystem.getFileObject(relativeLocalDirectory, repositoryFilePath);
    clientEnvironment.getLocalFileWriter().removeLocalFile(fileObject, fileSystem, clientEnvironment.getFileReadOnlyHandler());
    clientEnvironment.getAdminWriter().removeEntryForFile(fileObject, fileSystem);
    responseServices.getEventSender().notifyFileInfoListeners(
      new UpdatedFileInfo(fileObject, fileSystem.getLocalFileSystem().getFile(fileObject), UpdatedFileInfo.UpdatedType.REMOVED, null));
  }

  @Override
  public void processRemoveEntryResponse(String relativeLocalDirectory,
                                         String repositoryFilePath,
                                         IResponseServices responseServices,
                                         IClientEnvironment clientEnvironment) throws IOException {
    final ICvsFileSystem fileSystem = clientEnvironment.getCvsFileSystem();
    final FileObject fileObject = fileSystem.getFileObject(relativeLocalDirectory, repositoryFilePath);
    clientEnvironment.getAdminWriter().removeEntryForFile(fileObject, fileSystem);
    responseServices.getEventSender().notifyEntryListeners(fileObject, null);
  }

  @Override
  public void processCopyFileResponse(String relativeLocalDirectory,
                                      String repositoryFilePath,
                                      String newName,
                                      IClientEnvironment clientEnvironment) throws IOException {
    final FileObject fileObject = clientEnvironment.getCvsFileSystem().getFileObject(relativeLocalDirectory, repositoryFilePath);
    clientEnvironment.getLocalFileWriter().renameLocalFile(fileObject, clientEnvironment.getCvsFileSystem(), newName);
  }

  @Override
  public void processModTimeResponse(Date modifiedDate, IResponseServices responseServices) {
    // we assume the date is in GMT, this appears to be the case
    // We remove the ending because SimpleDateFormat does not parse
    // +xxxx, only GMT+xxxx and this avoid us having to do String
    // concat
    responseServices.setNextFileDate(modifiedDate);
  }

  @Override
  public void processModeResponse(String mode, IResponseServices responseServices) {
    responseServices.setNextFileMode(mode);
  }

  @Override
  public void processTemplateResponse(String relativeLocalDirectory,
                                      String repositoryFilePath,
                                      int length,
                                      IClientEnvironment clientEnvironment,
                                      IConnectionStreams connectionStreams) throws IOException {
    final DirectoryObject directoryObject =
      clientEnvironment.getCvsFileSystem().getDirectoryObject(relativeLocalDirectory, repositoryFilePath);

    clientEnvironment.getAdminWriter().writeTemplateFile(directoryObject, length, connectionStreams.getInputStream(),
                                                         connectionStreams.getReaderFactory(), clientEnvironment);
  }

  @Override
  public void processModuleExpansionResponse(String localPath, IResponseServices responseServices) {
    responseServices.getEventSender().notifyModuleExpansionListeners(localPath);
  }

  @Override
  public void processUpdatedResponse(String relativeLocalDirectory,
                                     String repositoryFilePath,
                                     String entryLine,
                                     String mode,
                                     int fileLength,
                                     IClientEnvironment clientEnvironment,
                                     IResponseServices responseServices,
                                     IConnectionStreams connectionStreams) throws IOException {
    processUpdatedMergedResponse(clientEnvironment, relativeLocalDirectory, repositoryFilePath, entryLine, responseServices, mode,
                                 fileLength, connectionStreams, false);
  }

  @Override
  public void processMergedResponse(String relativeLocalDirectory,
                                    String repositoryFilePath,
                                    String entryLine,
                                    String mode,
                                    int fileLength,
                                    IClientEnvironment clientEnvironment,
                                    IResponseServices responseServices,
                                    IConnectionStreams connectionStreams) throws IOException {
    processUpdatedMergedResponse(clientEnvironment, relativeLocalDirectory, repositoryFilePath, entryLine, responseServices, mode,
                                 fileLength, connectionStreams, true);
  }

  @Override
  public void processValidRequestsResponse(String validRequests, IResponseServices responseServices) {
  }

  // Utils ==================================================================

  private static void processUpdatedMergedResponse(IClientEnvironment clientEnvironment,
                                                   String relativeLocalDirectory,
                                                   String repositoryFilePath,
                                                   String entryLine,
                                                   IResponseServices responseServices,
                                                   String mode,
                                                   int fileLength,
                                                   IConnectionStreams connectionStreams,
                                                   boolean merged) throws IOException {
    final ICvsFileSystem cvsFileSystem = clientEnvironment.getCvsFileSystem();
    final FileObject fileObject = cvsFileSystem.getFileObject(relativeLocalDirectory, repositoryFilePath);
    final Entry entry = Entry.createEntryForLine(entryLine);

    final ILocalFileWriter localFileWriter = clientEnvironment.getLocalFileWriter();
    localFileWriter.setNextFileDate(responseServices.getNextFileDate());
    // TODO: really???
    localFileWriter.setNextFileMode(mode);

    final boolean binary = entry.isBinary();
    final boolean readOnly = FileUtils.isReadOnlyMode(mode);
    final Charset charSet = entry.isUnicode() ? StandardCharsets.UTF_16LE : null;
    writeFile(fileObject, fileLength, connectionStreams.getInputStream(), connectionStreams.getReaderFactory(), binary, readOnly,
              clientEnvironment, charSet);

    updateEntriesFileTime(fileObject, entry, cvsFileSystem, merged);

    final IAdminWriter adminWriter = clientEnvironment.getAdminWriter();
    adminWriter.ensureCvsDirectory(fileObject.getParent(), repositoryFilePath, clientEnvironment.getCvsRoot(), cvsFileSystem);
    adminWriter.setEntry(fileObject.getParent(), entry, cvsFileSystem);
    responseServices.getEventSender().notifyEntryListeners(fileObject, entry);
    responseServices.getEventSender().notifyFileInfoListeners(new UpdatedFileInfo(fileObject,
                                                                                  cvsFileSystem.getLocalFileSystem().getFile(fileObject),
                                                                                  merged
                                                                                  ? UpdatedFileInfo.UpdatedType.MERGED
                                                                                  : UpdatedFileInfo.UpdatedType.UPDATED, entry));
  }

  private static void writeFile(FileObject fileObject,
                                int length,
                                InputStream inputStream,
                                IReaderFactory readerFactory,
                                boolean binary,
                                final boolean readOnly,
                                IClientEnvironment clientEnvironment,
                                final Charset charSet) throws IOException {
    if (binary) {
      clientEnvironment.getLocalFileWriter().writeBinaryFile(fileObject, length, inputStream, readOnly,
                                                             clientEnvironment.getFileReadOnlyHandler(),
                                                             clientEnvironment.getCvsFileSystem());
    }
    else {
      clientEnvironment.getLocalFileWriter().writeTextFile(fileObject, length, inputStream, readOnly, readerFactory,
                                                           clientEnvironment.getFileReadOnlyHandler(),
                                                           clientEnvironment.getCvsFileSystem().getLocalFileSystem(), charSet);
    }
  }

  private static void updateEntriesFileTime(FileObject fileObject, Entry entry, ICvsFileSystem fileSystem, boolean merged) {
    if (entry.isAddedFile()) {
      entry.setDummyTimestamp();
    }
    else {
      final File file = fileSystem.getLocalFileSystem().getFile(fileObject);
      // we set the date the file was last modified in the Entry line
      // so that we can easily determine whether the file has been
      // untouched
      // for files with conflicts skip the setting of the conflict field.
      if (entry.isConflict()) {
        if (entry.isTimeStampMatchesFile()) {
          final Date date = new Date(file.lastModified());
          entry.parseConflictString(getEntryConflict(date, true, merged));
        }
        else {
          entry.parseConflictString(entry.getConflictStringWithoutConflict());
        }
      }
      else {
        final Date date = new Date(file.lastModified());
        entry.parseConflictString(getEntryConflict(date, false, merged));
      }
    }
  }

  @NonNls
  private static String getEntryConflict(Date date, boolean conflict, boolean merged) {
    if (merged) {
      if (conflict) {
        return "Result of merge+" + Entry.formatLastModifiedDate(date);
      }

      return "Result of merge";
    }
    return Entry.formatLastModifiedDate(date);
  }
}
