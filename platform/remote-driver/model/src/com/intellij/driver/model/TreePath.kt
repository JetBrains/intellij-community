package com.intellij.driver.model

import com.intellij.driver.model.transport.PassByValue
import java.io.Serializable

class TreePath(val path: List<String>): Serializable, PassByValue