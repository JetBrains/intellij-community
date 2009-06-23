package org.jetbrains.plugins.groovy.dsl

/**
 * @author peter
 */

interface ClassWrapper {

  String getQualifiedName()

  boolean isInheritor(String qname)

}