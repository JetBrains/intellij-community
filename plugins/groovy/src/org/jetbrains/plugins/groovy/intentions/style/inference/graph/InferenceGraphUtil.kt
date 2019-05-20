// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style.inference.graph



enum class RelationType {
  SUPER {
    override fun complement(): RelationType {
      return SUB
    }
  },
  SUB {
    override fun complement(): RelationType {
      return SUPER
    }
  };

  abstract fun complement(): RelationType
}

data class Relation(val left: InferenceUnit, val right: InferenceUnit, val type: RelationType)
