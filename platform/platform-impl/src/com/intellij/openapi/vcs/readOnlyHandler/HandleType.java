/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.io.ReadOnlyAttributeUtil;

import java.util.Collection;
import java.io.IOException;

public abstract class HandleType {
  private final String myName;
  private final boolean myUseVcs;

  public static final HandleType USE_FILE_SYSTEM = new HandleType(VcsBundle.message("handle.ro.file.status.type.using.file.system"), false) {
    public void processFiles(final Collection<VirtualFile> virtualFiles) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          try {
            for (VirtualFile file : virtualFiles) {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(file, false);
              file.refresh(false, false);
            }
          }
          catch (IOException e) {
            //ignore
          }
        }
      });
    }
  };

  protected HandleType(String name, boolean useVcs) {
    myName = name;
    myUseVcs = useVcs;
  }

  public String toString() {
    return myName;
  }

  public boolean getUseVcs() {
    return myUseVcs;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final HandleType that = (HandleType)o;

    if (myUseVcs != that.myUseVcs) return false;
    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myUseVcs ? 1 : 0);
    return result;
  }

  public abstract void processFiles(final Collection<VirtualFile> virtualFiles);
}
