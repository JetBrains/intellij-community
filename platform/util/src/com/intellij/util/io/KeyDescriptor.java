/*
 * @author max
 */
package com.intellij.util.io;

public interface KeyDescriptor<T> extends EqualityPolicy<T>, DataExternalizer<T> {
}