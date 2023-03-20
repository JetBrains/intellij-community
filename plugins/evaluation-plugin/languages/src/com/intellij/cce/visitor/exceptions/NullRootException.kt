package com.intellij.cce.visitor.exceptions

import java.lang.NullPointerException

class NullRootException(filePath: String) : NullPointerException("For file $filePath root element not found.")