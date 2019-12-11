// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Parses `VcsUser` from string correcting some simple mistakes.
 * The required format is: `user name <user.name@email.com>`
 */
internal object VcsUserParser {
  fun parse(project: Project, userValue: String): VcsUser? {
    val user = correct(userValue).takeIf { it.isNotBlank() } ?: return null
    val userRegistry = project.service<VcsUserRegistry>()

    val openBrace = user.indexOf('<')
    val closeBrace = user.lastIndexOf('>')
    if (openBrace < 0 || closeBrace != user.length - 1) return userRegistry.createUser(user, "")

    val name = user.substring(0, openBrace).trim()
    val email = user.substring(openBrace + 1, closeBrace).trim()
    return userRegistry.createUser(name, email)
  }

  fun correct(userValue: String): String {
    var user = userValue.trim()
    val openBrace = user.indexOf('<')
    val closeBrace = user.indexOf('>')

    if (openBrace < 0) { // email should open with "<"
      val at = user.lastIndexOf("@")
      if (at < 0) return user

      val email = user.lastIndexOf(' ', at - 1)
      if (email < 0) return user

      user = user.substring(0, email + 1) + "<" + user.substring(email + 1)
    }
    else if (openBrace > 0 && user[openBrace - 1] != ' ') { // insert space before email
      user = user.substring(0, openBrace) + " " + user.substring(openBrace)
    }

    if (closeBrace < 0) { // email should close with ">"
      user += ">"
    }

    return user
  }
}
