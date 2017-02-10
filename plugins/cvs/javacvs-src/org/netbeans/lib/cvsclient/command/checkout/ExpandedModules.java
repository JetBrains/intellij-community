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
package org.netbeans.lib.cvsclient.command.checkout;

import org.netbeans.lib.cvsclient.event.ICvsListener;
import org.netbeans.lib.cvsclient.event.ICvsListenerRegistry;
import org.netbeans.lib.cvsclient.event.IModuleExpansionListener;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Thomas Singer
 */
final class ExpandedModules implements ICvsListener, IModuleExpansionListener {

  // Fields =================================================================

  private final List<String> modules = new LinkedList<>();

  // Setup ==================================================================

  public ExpandedModules() {
  }

  // Implemented ============================================================

  public void moduleExpanded(String module) {
    modules.add(module);
  }

  public void registerListeners(ICvsListenerRegistry listenerRegistry) {
    listenerRegistry.addModuleExpansionListener(this);
  }

  public void unregisterListeners(ICvsListenerRegistry listenerRegistry) {
    listenerRegistry.removeModuleExpansionListener(this);
  }

  // Accessing ==============================================================

  public List<String> getModules() {
    return Collections.unmodifiableList(modules);
  }
}
