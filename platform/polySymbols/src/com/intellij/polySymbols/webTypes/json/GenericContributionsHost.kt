// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.json

import com.fasterxml.jackson.annotation.JsonAnyGetter

interface GenericContributionsHost {

  @get:JsonAnyGetter
  val additionalProperties: MutableMap<String, out MutableList<out GenericContributionOrProperty?>?>
}
