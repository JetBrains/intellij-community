// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.IconProductSize
import javax.swing.Icon

object NameMappings {

  private val ideName2Abbreviation = mapOf(
    "AppCode" to "AC",
    "Aqua" to "Aqua",
    "CLion" to "CL",
    "CLionNova" to "CL",
    "DataGrip" to "DG",
    "DataSpell" to "DS",
    "GoLand" to "GO",
    "IdeaIC" to "IC",
    "IntelliJIdea" to "IU",
    "MPS" to "MPS",
    "PyCharm" to "PY",
    "PhpStorm" to "PS",
    "PyCharmCE" to "PC",
    "Rider" to "RD",
    "RubyMine" to "RM",
    "RustRover" to "RR",
    "WebStorm" to "WS",
  )

  private val ideName2Icon20 = mapOf(
    "AppCode" to StartupImportIcons.IdeIcons.AC_20,
    "Aqua" to StartupImportIcons.IdeIcons.Aqua_20,
    "CLion" to StartupImportIcons.IdeIcons.CL_20,
    "CLionNova" to StartupImportIcons.IdeIcons.CL_20,
    "DataGrip" to StartupImportIcons.IdeIcons.DG_20,
    "DataSpell" to StartupImportIcons.IdeIcons.DS_20,
    "GoLand" to StartupImportIcons.IdeIcons.GO_20,
    "IdeaIC" to StartupImportIcons.IdeIcons.IC_20,
    "IntelliJIdea" to StartupImportIcons.IdeIcons.IU_20,
    "MPS" to StartupImportIcons.IdeIcons.MPS_20,
    "PyCharm" to StartupImportIcons.IdeIcons.PY_20,
    "PhpStorm" to StartupImportIcons.IdeIcons.PS_20,
    "PyCharmCE" to StartupImportIcons.IdeIcons.PC_20,
    "Rider" to StartupImportIcons.IdeIcons.RD_20,
    "RubyMine" to StartupImportIcons.IdeIcons.RM_20,
    "RustRover" to StartupImportIcons.IdeIcons.RR_20,
    "WebStorm" to StartupImportIcons.IdeIcons.WS_20
  )
  private val ideName2Icon24 = mapOf(
    "AppCode" to StartupImportIcons.IdeIcons.AC_24,
    "Aqua" to StartupImportIcons.IdeIcons.Aqua_24,
    "CLion" to StartupImportIcons.IdeIcons.CL_24,
    "CLionNova" to StartupImportIcons.IdeIcons.CL_24,
    "DataGrip" to StartupImportIcons.IdeIcons.DG_24,
    "DataSpell" to StartupImportIcons.IdeIcons.DS_24,
    "GoLand" to StartupImportIcons.IdeIcons.GO_24,
    "IdeaIC" to StartupImportIcons.IdeIcons.IC_24,
    "IntelliJIdea" to StartupImportIcons.IdeIcons.IU_24,
    "MPS" to StartupImportIcons.IdeIcons.MPS_24,
    "PyCharm" to StartupImportIcons.IdeIcons.PY_24,
    "PhpStorm" to StartupImportIcons.IdeIcons.PS_24,
    "PyCharmCE" to StartupImportIcons.IdeIcons.PC_24,
    "Rider" to StartupImportIcons.IdeIcons.RD_24,
    "RubyMine" to StartupImportIcons.IdeIcons.RM_24,
    "RustRover" to StartupImportIcons.IdeIcons.RR_24,
    "WebStorm" to StartupImportIcons.IdeIcons.WS_24
  )
  private val ideName2Icon48 = mapOf(
    "AppCode" to StartupImportIcons.IdeIcons.AC_48,
    "Aqua" to StartupImportIcons.IdeIcons.Aqua_48,
    "CLion" to StartupImportIcons.IdeIcons.CL_48,
    "CLionNova" to StartupImportIcons.IdeIcons.CL_48,
    "DataGrip" to StartupImportIcons.IdeIcons.DG_48,
    "DataSpell" to StartupImportIcons.IdeIcons.DS_48,
    "GoLand" to StartupImportIcons.IdeIcons.GO_48,
    "IdeaIC" to StartupImportIcons.IdeIcons.IC_48,
    "IntelliJIdea" to StartupImportIcons.IdeIcons.IU_48,
    "MPS" to StartupImportIcons.IdeIcons.MPS_48,
    "PyCharm" to StartupImportIcons.IdeIcons.PY_48,
    "PhpStorm" to StartupImportIcons.IdeIcons.PS_48,
    "PyCharmCE" to StartupImportIcons.IdeIcons.PC_48,
    "Rider" to StartupImportIcons.IdeIcons.RD_48,
    "RubyMine" to StartupImportIcons.IdeIcons.RM_48,
    "RustRover" to StartupImportIcons.IdeIcons.RR_48,
    "WebStorm" to StartupImportIcons.IdeIcons.WS_48
  )

  private val ideName2FullName = mapOf(
    "AppCode" to "AppCode",
    "Aqua" to "Aqua",
    "CLion" to "CLion",
    "CLionNova" to "CLion with Radler",
    "DataGrip" to "DataGrip",
    "DataSpell" to "DataSpell",
    "GoLand" to "GoLand",
    "IdeaIC" to "IDEA CE",
    "IntelliJIdea" to "IDEA Ultimate",
    "MPS" to "MPS",
    "PyCharm" to "PyCharm",
    "PhpStorm" to "PhpStorm",
    "PyCharmCE" to "PyCharm CE",
    "Rider" to "Rider",
    "RubyMine" to "RubyMine",
    "RustRover" to "RustRover",
    "WebStorm" to "WebStorm",
  )

  fun getIcon(ideName: String, iconSize: IconProductSize): Icon? {
    if (iconSize == IconProductSize.SMALL) {
      return ideName2Icon20[ideName]
    } else if (iconSize == IconProductSize.MIDDLE) {
      return ideName2Icon24[ideName]
    } else {
      return ideName2Icon48[ideName]
    }
  }

  fun getFullName(ideName: String) : String {
    return ideName2FullName[ideName] ?: ideName
  }

}