package com.intellij.appengine.descriptor.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;

/**
 * @author nik
 */
public interface AppEngineWebApp extends DomElement {
  GenericDomValue<String> getApplication();
}
