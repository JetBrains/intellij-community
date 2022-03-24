package com.intellij.cce.workspace.info

import com.intellij.cce.core.Session

data class FileSessionsInfo(val filePath: String, val text: String, val sessions: List<Session>)