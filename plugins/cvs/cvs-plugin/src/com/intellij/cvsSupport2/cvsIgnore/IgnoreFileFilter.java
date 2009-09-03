package com.intellij.cvsSupport2.cvsIgnore;

import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfo;
import com.intellij.cvsSupport2.cvsIgnore.IgnoredFilesInfoImpl;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.util.containers.HashMap;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;
import org.netbeans.lib.cvsclient.file.ICvsFileSystem;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Map;

/**
 * author: lesya
 */

public class IgnoreFileFilter implements IIgnoreFileFilter{
  private final Map<File, IgnoredFilesInfo> myParentToFilterMap = new HashMap<File,IgnoredFilesInfo>();

  public boolean shouldBeIgnored(AbstractFileObject abstractFileObject, ICvsFileSystem cvsFileSystem) {
    File file = cvsFileSystem.getLocalFileSystem().getFile(abstractFileObject);
    File parent = file.getParentFile();
    if (!myParentToFilterMap.containsKey(parent)){
      myParentToFilterMap.put(parent, IgnoredFilesInfoImpl.createForFile(new File(parent,
                                                                                  CvsUtil.CVS_IGNORE_FILE)));
    }
    return myParentToFilterMap.get(parent).shouldBeIgnored(file.getName());

  }
}
