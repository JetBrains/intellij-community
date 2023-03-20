package com.intellij.cce.workspace.filter

abstract class ComparePositionFilter(override val name: String, override val evaluationType: String) :
  CompareSessionsFilter {
  protected fun upper(basePosition: Int, forComparingPosition: Int): Boolean = basePosition < forComparingPosition && basePosition != -1
  protected fun exist(basePosition: Int, forComparingPosition: Int): Boolean = basePosition != -1 && forComparingPosition == -1
}