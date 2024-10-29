@file:Suppress("MISSING_DEPENDENCY_SUPERCLASS") // mockSDK misses some superclasses

import javax.swing.JComponent
import com.intellij.openapi.actionSystem.DataProvider

class <warning descr="Use UiDataProvider instead of DataProvider">MyJComponent</warning> : JComponent(), DataProvider {}