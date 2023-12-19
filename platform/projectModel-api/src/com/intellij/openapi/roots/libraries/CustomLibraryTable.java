// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.libraries;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * The CustomLibraryTable interface extends the LibraryTable interface and represents a custom library table.
 * It provides methods to read and write the library table from/to an XML element.
 * <p>
 * The direct usage of these methods has to be provided by the client, not by the platform,
 * so client's code is responsible for serialization of custom libraries to/from disk
 */
public interface CustomLibraryTable extends LibraryTable {
  void readExternal(final Element element) throws InvalidDataException;
  void writeExternal(final Element element) throws WriteExternalException;
}
