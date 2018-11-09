/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.util.containers.SLRUCache;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.CvsRoot;
import org.netbeans.lib.cvsclient.IClientEnvironment;
import org.netbeans.lib.cvsclient.admin.*;
import org.netbeans.lib.cvsclient.command.IOCommandException;
import org.netbeans.lib.cvsclient.file.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

public class CheckoutAdminWriter implements IAdminWriter {
  private final AdminWriter myDelegate;
  private final CheckoutEntriesWriter myEntriesCreator;

  public CheckoutAdminWriter(final String lineSeparator, final String charset) {
    myEntriesCreator = new CheckoutEntriesWriter(charset, lineSeparator);
    myDelegate = new AdminWriter(lineSeparator, charset, myEntriesCreator);
  }

  @Override
  public void ensureCvsDirectory(final DirectoryObject directoryObject,
                                 final String repositoryPath,
                                 final CvsRoot cvsRoot, final ICvsFileSystem cvsFileSystem) throws IOException {
    myDelegate.ensureCvsDirectory(directoryObject, repositoryPath, cvsRoot, cvsFileSystem);
  }

  @Override
  public void setEntry(final DirectoryObject directoryObject, final Entry entry, final ICvsFileSystem cvsFileSystem) throws IOException {
    myDelegate.setEntry(directoryObject, entry, cvsFileSystem);
  }

  @Override
  public void removeEntryForFile(final AbstractFileObject fileObject, final ICvsFileSystem cvsFileSystem) throws IOException {
    myDelegate.removeEntryForFile(fileObject, cvsFileSystem);
  }

  @Override
  public void pruneDirectory(final DirectoryObject directoryObject, final ICvsFileSystem cvsFileSystem) {
    myDelegate.pruneDirectory(directoryObject, cvsFileSystem);
  }

  @Override
  public void editFile(final FileObject fileObject,
                       final Entry entry, final ICvsFileSystem cvsFileSystem, final IFileReadOnlyHandler fileReadOnlyHandler)
    throws IOException {
    myDelegate.editFile(fileObject, entry, cvsFileSystem, fileReadOnlyHandler);
  }

  @Override
  public void uneditFile(final FileObject fileObject, final ICvsFileSystem cvsFileSystem, final IFileReadOnlyHandler fileReadOnlyHandler)
    throws IOException {
    myDelegate.uneditFile(fileObject, cvsFileSystem, fileReadOnlyHandler);
  }

  @Override
  public void setStickyTagForDirectory(final DirectoryObject directoryObject, final String tag, final ICvsFileSystem cvsFileSystem)
    throws IOException {
    myDelegate.setStickyTagForDirectory(directoryObject, tag, cvsFileSystem);
  }

  @Override
  public void setEntriesDotStatic(final DirectoryObject directoryObject, final boolean set, final ICvsFileSystem cvsFileSystem)
    throws IOException {
    myDelegate.setEntriesDotStatic(directoryObject, set, cvsFileSystem);
  }

  @Override
  public void writeTemplateFile(final DirectoryObject directoryObject, final int fileLength, final InputStream inputStream,
                                final IReaderFactory readerFactory, final IClientEnvironment clientEnvironment) throws IOException {
    myDelegate.writeTemplateFile(directoryObject, fileLength, inputStream, readerFactory, clientEnvironment);
  }

  @Override
  public void directoryAdded(final DirectoryObject directory, final ICvsFileSystem cvsFileSystem) throws IOException {
    myDelegate.directoryAdded(directory, cvsFileSystem);
  }

  public void finish() throws IOCommandException {
    myEntriesCreator.finish();
  }

  private static class CheckoutEntriesWriter implements EntriesWriter {
    private final MyCache myCache;

    private CheckoutEntriesWriter(final String charset, final String lineSeparator) {
      myCache = new MyCache(charset, lineSeparator);
    }

    @Override
    public void addEntry(final File directory, final Entry entry) throws IOException {
      try {
        final EntriesHandler handler = myCache.get(directory.getAbsolutePath());
        handler.getEntries().addEntry(entry);
      }
      catch (MyWrappedIOException e) {
        throw e.getInner();
      }
    }

    public void finish() throws IOCommandException {
      myCache.finish();
    }
  }

  private static class MyCache extends SLRUCache<String, EntriesHandler> {
    private final String myCharset;
    private final String myLineSeparator;

    private MyCache(final String charset, final String lineSeparator) {
      super(1600, 224);
      myCharset = charset;
      myLineSeparator = lineSeparator;
    }

    @Override
    @NotNull
    public EntriesHandler createValue(final String key) {
      try {
        final EntriesHandler result = new EntriesHandler(new File(key));
        result.read(myCharset);
        return result;
      }
      catch (IOException e) {
        throw new MyWrappedIOException(e);
      }
    }

    @Override
    protected void onDropFromCache(final String key, @NotNull final EntriesHandler value) {
      writeHandler(value);
    }

    private void writeHandler(final EntriesHandler value) {
      try {
        value.write(myLineSeparator, myCharset);
      }
      catch (IOException e) {
        throw new MyWrappedIOException(e);
      }
    }

    public void finish() throws IOCommandException {
      final Set<Map.Entry<String,EntriesHandler>> entries = entrySet();
      try {
        for (Map.Entry<String, EntriesHandler> entry : entries) {
          entry.getValue().write(myLineSeparator, myCharset);
        }
      }
      catch (IOException e) {
        throw new IOCommandException(e);
      }
    }
  }

  private static class MyWrappedIOException extends RuntimeException {
    private final IOException myInner;

    private MyWrappedIOException(final IOException inner) {
      myInner = inner;
    }

    public IOException getInner() {
      return myInner;
    }
  }
}
