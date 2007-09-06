package com.intellij.cvsSupport2.cvsoperations.javacvsSpecificImpls;

import org.netbeans.lib.cvsclient.file.*;
import org.netbeans.lib.cvsclient.IConnectionStreams;
import java.io.IOException;
import java.util.Collection;

/**
 * author: lesya
 */
public class ConstantLocalFileReader implements ILocalFileReader{

  private final boolean myFileExists;

  public static final ConstantLocalFileReader FOR_EXISTING_FILE = new ConstantLocalFileReader(true);

  private ConstantLocalFileReader(boolean fileExists) {
    myFileExists = fileExists;
  }

  public boolean exists(AbstractFileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return myFileExists;
  }

  public void transmitBinaryFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void transmitTextFile(FileObject fileObject, IConnectionStreams connectionStreams, ICvsFileSystem cvsFileSystem) throws IOException {
  }

  public void listFilesAndDirectories(DirectoryObject directoryObject, Collection<String> fileNames, Collection<String> directoryNames, ICvsFileSystem cvsFileSystem) {
  }

  public boolean isWritable(FileObject fileObject, ICvsFileSystem cvsFileSystem) {
    return false;
  }
}
