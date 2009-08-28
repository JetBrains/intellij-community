package com.intellij.openapi.util;

/**
 * Label for JDOMEternalizable. If component implements this interface it will
 * not be passed to external StreamProvider components (for example, it will not be stored on IDEAServer)
 */
public interface RoamingTypeDisabled {
}
