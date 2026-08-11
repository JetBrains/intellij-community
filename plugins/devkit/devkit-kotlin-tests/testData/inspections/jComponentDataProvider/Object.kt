@file:Suppress(<warning descr="[ERROR_SUPPRESSION]">"MISSING_DEPENDENCY_SUPERCLASS"</warning>) // mockSDK misses some superclasses

import javax.swing.JComponent
import com.intellij.openapi.actionSystem.DataProvider

private val table = <warning descr="Use UiDataProvider instead of DataProvider">object</warning> : JComponent(), DataProvider {}