@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS") // mockSDK misses some superclasses

import javax.swing.JComponent
import com.intellij.openapi.actionSystem.DataProvider

private val table = <warning descr="Use UiDataProvider instead of DataProvider">object</warning> : JComponent(), DataProvider {}