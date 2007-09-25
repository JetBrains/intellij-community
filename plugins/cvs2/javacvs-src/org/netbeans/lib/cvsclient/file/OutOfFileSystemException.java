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
package org.netbeans.lib.cvsclient.file;

import org.jetbrains.annotations.NonNls;

/**
 * @author Thomas Singer
 */
public final class OutOfFileSystemException extends RuntimeException {

  // Setup ==================================================================

  public OutOfFileSystemException(String file, String rootPath) {
    super(createMessage(file, rootPath));
  }

  @NonNls
  private static String createMessage(final String file, final String rootPath) {
    return "File " + file + " not contained in file system below root " + rootPath;
  }
}
