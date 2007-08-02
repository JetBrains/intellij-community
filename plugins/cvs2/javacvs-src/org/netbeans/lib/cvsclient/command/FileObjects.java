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
package org.netbeans.lib.cvsclient.command;

import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.file.AbstractFileObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Thomas Singer
 */
public final class FileObjects {

  // Fields =================================================================

  private final List<AbstractFileObject> fileObjects = new ArrayList<AbstractFileObject>();

  // Accessing ==============================================================

  public void addFileObject(@NotNull AbstractFileObject file) {
    fileObjects.add(file);
  }

  public List<AbstractFileObject> getFileObjects() {
    return Collections.unmodifiableList(fileObjects);
  }
}
