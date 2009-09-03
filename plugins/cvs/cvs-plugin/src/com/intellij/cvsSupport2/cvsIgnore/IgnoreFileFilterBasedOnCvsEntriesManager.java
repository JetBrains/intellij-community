package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;

/**
 * author: lesya
 */
public class IgnoreFileFilterBasedOnCvsEntriesManager implements IIgnoreFileFilter{
  public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
    File file = cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject);
    VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(file);
    if (virtualFile == null) return false;
    IgnoredFilesInfo filter = CvsEntriesManager.getInstance().getFilter(virtualFile.getParent());
    return filter.shouldBeIgnored(file.getName());
  }
}
