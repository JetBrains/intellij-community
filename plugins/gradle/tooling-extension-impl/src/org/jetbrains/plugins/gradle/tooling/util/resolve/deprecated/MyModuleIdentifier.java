// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.util.resolve.deprecated;

import java.util.Objects;

/**
 * @deprecated use org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl
 */
@Deprecated
class MyModuleIdentifier {
  String name;
  String group;

  MyModuleIdentifier(String name, String group) {
    this.name = name;
    this.group = group;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MyModuleIdentifier that = (MyModuleIdentifier)o;

    if (!Objects.equals(name, that.name)) return false;
    if (!Objects.equals(group, that.group)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (group != null ? group.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return group + ":" + name;
  }
}
