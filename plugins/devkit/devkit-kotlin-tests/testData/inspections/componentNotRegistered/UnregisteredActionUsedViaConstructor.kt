@file:Suppress("UNUSED_VARIABLE")

import com.intellij.openapi.actionSystem.AnAction

class UnregisteredActionUsedViaConstructor : AnAction()

fun main(args: Array<String>) {
  val mine = UnregisteredActionUsedViaConstructor()
}