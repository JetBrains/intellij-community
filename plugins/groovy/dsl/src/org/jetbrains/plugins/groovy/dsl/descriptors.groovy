package org.jetbrains.plugins.groovy.dsl

import java.beans.MethodDescriptor

/**
 * @author peter
 */

interface ClassDescriptor {

  String getQualifiedName()

  boolean isInheritor(String qname)

}

interface ScriptDescriptor extends ClassDescriptor {
  String getExtension()
}