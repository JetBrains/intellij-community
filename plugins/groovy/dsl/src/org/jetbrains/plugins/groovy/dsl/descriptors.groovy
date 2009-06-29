package org.jetbrains.plugins.groovy.dsl

/**
 * @author peter
 */

interface ClassDescriptor {

  String getQualifiedName()

  boolean isInheritor(String qname)

  MethodDescriptor[] getMethods()

}
interface MethodDescriptor {
  String getName()

  ParameterDescriptor[] getParameters()
}

interface ParameterDescriptor {
  String getName()

  TypeDescriptor getType()

  void setType(TypeDescriptor descriptor)
}

interface TypeDescriptor {
}

interface ClassTypeDescriptor extends TypeDescriptor {
  ClassDescriptor getClazz()
}

interface MapDescriptor extends TypeDescriptor {
  TypeDescriptor getAt(String name)

  void putAt(String name, TypeDescriptor descriptor)
}

interface ClosureDescriptor extends TypeDescriptor {
  ParameterDescriptor[] getParameters()
}
