// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Provide an ability to store libraries in a custom library table. This functionality is considered as internal. Plugins are supposed to
 * use the standard module-level, project-level or application-level libraries.
 * <p/>                                                                           
 * The platform doesn't provide editors for libraries from custom tables and doesn't save their configurations automatically, so a plugin 
 * providing such a table must implement this functionality on its own. Modules from custom tables may be added to dependencies of modules 
 * in any project in 'Project Structure' dialog.
 *
 * <p>The implementation should be registered in your {@code plugin.xml}:
 * <pre>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;
 * &nbsp;&nbsp;&lt;customLibraryTable implementation="qualified-class-name"/&gt;
 * &lt;/extensions&gt;
 * </pre>
 * </p>
 */
@ApiStatus.Internal
public interface CustomLibraryTableDescription {
  /**
   * Returns unique ID of the table. Must not be equal to one the standard levels ("application", "project", "module").
   */
  @NotNull String getTableLevel();

  @NotNull LibraryTablePresentation getPresentation();

  ExtensionPointName<CustomLibraryTableDescription> CUSTOM_TABLES_EP = new ExtensionPointName<>("com.intellij.customLibraryTable");
}
