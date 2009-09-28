package org.jetbrains.plugins.groovy.config.util;

import com.intellij.openapi.roots.libraries.Library;

/**
 * @author ilyas
 */
public interface LibrarySDK extends AbstractSDK{

  Library getLibrary();
}
