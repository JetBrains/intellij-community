/*
 *                 Sun Public License Notice
 *
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 *
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2000 Sun
 * Microsystems, Inc. All Rights Reserved.
 */
package org.netbeans.lib.cvsclient.admin;

import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.SmartCvsSrcBundle;
import org.netbeans.lib.cvsclient.file.*;
import org.netbeans.lib.cvsclient.util.BugLog;
import org.jetbrains.annotations.NonNls;

import java.io.*;

import com.intellij.openapi.util.io.FileUtil;

/**
 * A handler for administrative information that maintains full compatibility
 * with the one employed by the original C implementation of a CVS client.
 * <p>This implementation strives to provide complete compatibility with
 * the standard CVS client, so that operations on locally checked-out
 * files can be carried out by either this library or the standard client
 * without causing the other to fail. Any such failure should be considered
 * a bug in this library.
 * @author  Robert Greig
 */
public final class AdminWriter
        implements IAdminWriter {

         private final String myLineSeparator;
         private final String myCharset;

         // for tests only!
         public static boolean WRITE_RELATIVE_PATHS = true;

  @NonNls private static final String CVS_DIR_NAME = "CVS";
  @NonNls private static final String TAG_FILE_NAME = "Tag";
  @NonNls private static final String ENTRIES_STATIC_FILE_NAME = "Entries.Static";
  @NonNls private static final String CVS_TEMPLATE_FILE_PATH = "CVS/Template";
  @NonNls private static final String ROOT_FILE_NAME = "Root";
  @NonNls private static final String REPOSITORY_FILE_NAME = "Repository";
  @NonNls private static final String ENTRIES_FILE_NAME = "Entries";
  @NonNls private static final String CVS_ROOT_FILE_PATH = "CVS/Root";
  @NonNls private static final String CVS_REPOSITORY_FILE_PATH = "CVS/Repository";
  @NonNls private static final String CVS_BASE_FILE_PATH = "CVS/Base/";
  @NonNls private static final String CVS_BASEREV_FILE_PATH = "CVS/Baserev";

  // Setup ==================================================================

        public AdminWriter(String lineSeparator, final String charset) {
          myLineSeparator = lineSeparator;
          myCharset = charset;
        }

        // Implemented ============================================================

        public void ensureCvsDirectory(DirectoryObject directoryObject, String repositoryPath, CvsRoot cvsRoot, ICvsFileSystem cvsFileSystem) throws IOException {
                final File cvsDirectory = ensureCvsDirectory(directoryObject, cvsFileSystem);
                // now ensure that the Root and Repository files exist
                ensureExistingRootFile(cvsDirectory, cvsRoot);
                ensureRepositoryFile(cvsDirectory, WRITE_RELATIVE_PATHS ? cvsFileSystem.getRelativeRepositoryPath(repositoryPath) : repositoryPath);
                ensureExistingEntriesFile(cvsDirectory);
        }

        public void removeEntryForFile(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
                final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
                final File directory = file.getParentFile();
                if (directory == null) {
                        throw new IOException(SmartCvsSrcBundle.message("file.does.not.have.a.parent.directory.error.message", file));
                }

                final EntriesHandler entriesHandler = new EntriesHandler(directory);
                final boolean entriesUpdated = entriesHandler.read(myCharset);
                final boolean entryRemoved = entriesHandler.getEntries().removeEntry(file.getName());
                if (entriesUpdated || entryRemoved) {
                        entriesHandler.write(myLineSeparator, myCharset);
                }
        }

        public void pruneDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
                deleteDirectoryRecursively(cvsFileSystem.getLocalFileSystem().getFile(directoryObject));
                deleteDirectoryRecursively(cvsFileSystem.getAdminFileSystem().getFile(directoryObject));
        }

        public void setStickyTagForDirectory(DirectoryObject directoryObject, String tag, ICvsFileSystem cvsFileSystem) throws IOException {
                final File cvsDirectory = new File(cvsFileSystem.getAdminFileSystem().getFile(directoryObject), CVS_DIR_NAME);
                if (!cvsDirectory.isDirectory()) {
                        return;
                }

                final File tagFile = new File(cvsDirectory, TAG_FILE_NAME);
                if (tag != null) {
                        FileUtils.writeLine(tagFile, tag);
                }
                else {
                        tagFile.delete();
                }
        }

        public void editFile(FileObject fileObject, Entry entry, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
                createBaserevEntry(fileObject, cvsFileSystem, entry);

                final File localFile = cvsFileSystem.getLocalFileSystem().getFile(fileObject);
                FileUtils.copyFile(localFile, getEditBackupFile(fileObject, cvsFileSystem));
                fileReadOnlyHandler.setFileReadOnly(localFile, false);
        }

        public void uneditFile(FileObject fileObject, ICvsFileSystem cvsFileSystem, IFileReadOnlyHandler fileReadOnlyHandler) throws IOException {
                final File editBackupFile = getEditBackupFile(fileObject, cvsFileSystem);
                if (!editBackupFile.isFile()) {
                        return;
                }

                editBackupFile.delete();
                removeBaserevEntry(fileObject, cvsFileSystem);

                fileReadOnlyHandler.setFileReadOnly(cvsFileSystem.getLocalFileSystem().getFile(fileObject), true);
        }

        public void setEntriesDotStatic(DirectoryObject directoryObject, boolean set, ICvsFileSystem cvsFileSystem) throws IOException {
                final File localDirectory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);
                if (set) {
                        final File cvsDirectory = getCvsDirectoryForLocalDirectory(localDirectory);
                        if (cvsDirectory.exists()) {
                                final File staticFile = new File(cvsDirectory, ENTRIES_STATIC_FILE_NAME);
                                staticFile.createNewFile();
                        }
                }
                else {
                        final File entriesDotStaticFile = new File(getCvsDirectoryForLocalDirectory(localDirectory), ENTRIES_STATIC_FILE_NAME);
                        if (entriesDotStaticFile.exists()) {
                                entriesDotStaticFile.delete();
                        }
                }
        }

        /**
         * Set the Entry in the specified directory.
         * @throws IOException if an error occurs writing the details
         */
        public void setEntry(DirectoryObject directoryObject, Entry entry, ICvsFileSystem cvsFileSystem) throws IOException {
                BugLog.getInstance().assertNotNull(entry);

                final File directory = cvsFileSystem.getAdminFileSystem().getFile(directoryObject);

                final EntriesHandler entriesHandler = new EntriesHandler(directory);
                entriesHandler.read(myCharset);
                entriesHandler.getEntries().addEntry(entry);
                entriesHandler.write(myLineSeparator, myCharset);
        }

        public void writeTemplateFile(DirectoryObject directoryObject, int fileLength, InputStream inputStream, IReaderFactory readerFactory, IClientEnvironment clientEnvironment) throws IOException {
                final FileObject fileObject = FileObject.createInstance(directoryObject, CVS_TEMPLATE_FILE_PATH);
                final IFileSystem adminFileSystem = clientEnvironment.getCvsFileSystem().getAdminFileSystem();
                if (fileLength == 0) {
                        adminFileSystem.getFile(fileObject).delete();
                        return;
                }

                clientEnvironment.getLocalFileWriter().writeTextFile(fileObject, fileLength, inputStream, false, readerFactory, clientEnvironment.getFileReadOnlyHandler(), adminFileSystem,
                                                                     null);
        }

        public void directoryAdded(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
                final DirectoryObject parent = directoryObject.getParent();
                final String cvsRoot = getCvsRoot(parent, cvsFileSystem);
                final String repositoryPath = getRepository(parent, cvsFileSystem) + '/' + directoryObject.getName();
                final String stickyTag = AdminUtils.getStickyTagForDirectory(parent, cvsFileSystem);

                final File cvsDirectory = ensureCvsDirectory(directoryObject, cvsFileSystem);
                // now ensure that the Root and Repository files exist
                FileUtils.writeLine(new File(cvsDirectory, ROOT_FILE_NAME), cvsRoot);
                FileUtils.writeLine(new File(cvsDirectory, REPOSITORY_FILE_NAME), repositoryPath);
                new Entries().write(new File(cvsDirectory, ENTRIES_FILE_NAME), myLineSeparator, myCharset);
                setStickyTagForDirectory(directoryObject, stickyTag, cvsFileSystem);
                addDirectoryToParentEntriesFile(cvsDirectory.getParentFile());
        }

        // Utils ==================================================================

        private String getCvsRoot(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
                final File cvsRootFile = cvsFileSystem.getAdminFileSystem().getFile(FileObject.createInstance(directoryObject, CVS_ROOT_FILE_PATH));
                return FileUtils.readLineFromFile(cvsRootFile);
        }

        private String getRepository(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) throws IOException {
                final File cvsRootFile = cvsFileSystem.getAdminFileSystem().getFile(FileObject.createInstance(directoryObject, CVS_REPOSITORY_FILE_PATH));
                return FileUtils.readLineFromFile(cvsRootFile);
        }

        private File getCvsDirectoryForLocalDirectory(File directory) {
                return new File(directory, CVS_DIR_NAME);
        }

        private void deleteDirectoryRecursively(File directory) {
                final File[] files = directory.listFiles();
                if (files == null) {
                        return;
                }

                for (int i = 0; i < files.length; i++) {
                        final File file = files[i];
                        if (file.isDirectory()) {
                                deleteDirectoryRecursively(file);
                        }
                        else {
                                file.delete();
                        }
                }
                directory.delete();
        }

        private File ensureCvsDirectory(DirectoryObject directoryObject, ICvsFileSystem cvsFileSystem) {
                final File cvsDirectory = new File(cvsFileSystem.getAdminFileSystem().getFile(directoryObject), CVS_DIR_NAME);
                cvsDirectory.mkdirs();
                return cvsDirectory;
        }

        private void ensureExistingRootFile(File cvsDirectory, CvsRoot cvsRoot) throws IOException {
                final File rootFile = new File(cvsDirectory, ROOT_FILE_NAME);
                if (rootFile.exists()) {
                        return;
                }

                FileUtils.writeLine(rootFile, cvsRoot.getCvsRoot());
        }

        private void ensureRepositoryFile(File cvsDirectory, String repositoryPath) throws IOException {
                final File repositoryFile = new File(cvsDirectory, REPOSITORY_FILE_NAME);
                if (repositoryFile.exists()) {
                        return;
                }

                FileUtils.writeLine(repositoryFile, repositoryPath);
        }

        private void ensureExistingEntriesFile(File cvsDirectory) throws IOException {
                final File entriesFile = new File(cvsDirectory, ENTRIES_FILE_NAME);
                if (entriesFile.exists()) {
                        return;
                }

                new Entries().write(entriesFile, myLineSeparator, myCharset);

                // need to know if we had to create any directories so that we can
                // update the CVS/Entries file in the *parent* director
                addDirectoryToParentEntriesFile(cvsDirectory.getParentFile());
        }

        private void addDirectoryToParentEntriesFile(File directory) throws IOException {
                try {
                        final EntriesHandler entriesHandler = new EntriesHandler(directory.getParentFile());
                        entriesHandler.read(myCharset);
                        entriesHandler.getEntries().addEntry(Entry.createDirectoryEntry(directory.getName()));
                        entriesHandler.write(myLineSeparator, myCharset);
                }
                catch (FileNotFoundException ex) {
                        // The Entries file will not exist in the case where this is the top level of the module
                }
        }

        private void createBaserevEntry(FileObject fileObject, ICvsFileSystem cvsFileSystem, Entry entry) throws IOException {
                if (entry == null || entry.getRevision() == null || entry.isAddedFile() || entry.isRemoved()) {
                        throw new IllegalArgumentException("File does not have an Entry or Entry is invalid!");
                }

                final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
                final File baserevFile = new File(file.getParentFile(), CVS_BASEREV_FILE_PATH);
                final File backupFile = new File(baserevFile.getAbsolutePath() + '~');

                BufferedReader reader = null;
                BufferedWriter writer = null;
                boolean append = true;
                boolean writeFailed = true;
                final String entryStart = 'B' + file.getName() + '/';
                try {
                        writer = new BufferedWriter(new FileWriter(backupFile));
                        writeFailed = false;
                        reader = new BufferedReader(new FileReader(baserevFile));

                        for (String line = reader.readLine();
                             line != null;
                             line = reader.readLine()) {

                                if (line.startsWith(entryStart)) {
                                        append = false;
                                }
                                writeFailed = true;
                                writer.write(line);
                                writer.newLine();
                                writeFailed = false;
                        }
                }
                catch (IOException ex) {
                        if (writeFailed) {
                                throw ex;
                        }
                }
                finally {
                        if (reader != null) {
                                try {
                                        reader.close();
                                }
                                catch (IOException ex) {
                                        // ignore
                                }
                        }
                        if (writer != null) {
                                if (append && !writeFailed) {
                                        writer.write(entryStart + entry.getRevision() + '/');
                                        writer.newLine();
                                }

                                try {
                                        writer.close();
                                }
                                catch (IOException ex) {
                                        // ignore
                                }
                        }
                }
                baserevFile.delete();
                FileUtil.rename(backupFile, baserevFile);
        }

        private void removeBaserevEntry(FileObject fileObject, ICvsFileSystem cvsFileSystem) throws IOException {
                final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
                final File baserevFile = new File(file.getParentFile(), CVS_BASEREV_FILE_PATH);
                final File backupFile = new File(baserevFile.getAbsolutePath() + '~');

                BufferedReader reader = null;
                BufferedWriter writer = null;
                final String entryStart = 'B' + file.getName() + '/';
                try {
                        writer = new BufferedWriter(new FileWriter(backupFile));
                        reader = new BufferedReader(new FileReader(baserevFile));

                        for (String line = reader.readLine();
                             line != null;
                             line = reader.readLine()) {

                                if (line.startsWith(entryStart)) {
                                        continue;
                                }

                                writer.write(line);
                                writer.newLine();
                        }
                }
                catch (FileNotFoundException ex) {
                        // ignore
                }
                finally {
                        if (writer != null) {
                                try {
                                        writer.close();
                                }
                                catch (IOException ex) {
                                        // ignore
                                }
                        }
                        if (reader != null) {
                                try {
                                        reader.close();
                                }
                                catch (IOException ex) {
                                        // ignore
                                }
                        }
                }
                baserevFile.delete();
                if (backupFile.length() > 0) {
                        FileUtil.rename(backupFile, baserevFile);
                }
                else {
                        backupFile.delete();
                }
        }

        private File getEditBackupFile(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
                final File file = cvsFileSystem.getAdminFileSystem().getFile(fileObject);
                return new File(file.getParentFile(), CVS_BASE_FILE_PATH + file.getName());
        }
}
