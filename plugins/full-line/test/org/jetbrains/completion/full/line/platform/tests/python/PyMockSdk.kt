//package org.jetbrains.completion.full.line.platform.tests.python
//
//import com.intellij.openapi.projectRoots.Sdk
//import com.intellij.openapi.projectRoots.SdkAdditionalData
//import com.intellij.openapi.projectRoots.SdkTypeId
//import com.intellij.openapi.projectRoots.impl.MockSdk
//import com.intellij.openapi.roots.OrderRootType
//import com.intellij.openapi.vfs.LocalFileSystem
//import com.intellij.openapi.vfs.VirtualFile
//import com.intellij.util.containers.MultiMap
//import com.intellij.util.containers.addIfNotNull
//import com.jetbrains.python.PyNames
//import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil
//import com.jetbrains.python.psi.LanguageLevel
//import com.jetbrains.python.sdk.PythonSdkUtil
//import org.jdom.Element
//import org.jetbrains.annotations.NonNls
//import java.io.File
//
//object PythonMockSdk {
//    fun create(level: LanguageLevel, name: String?, dataPath: String): Sdk {
//        val mockName = name ?: "mock-sdk"
//        val roots = MultiMap<OrderRootType, VirtualFile>().apply {
//            putValues(OrderRootType.CLASSES, createRoots("$dataPath/$mockName"))
//        }
//
//        return MockSdk(
//            "Mock ${PyNames.PYTHON_SDK_ID_NAME} ${level.toPythonVersion()}",
//            "$dataPath/$mockName/bin/python",
//            toVersionString(level),
//            roots,
//            PyMockSdkType(level)
//        ).clone()
//    }
//
//    private fun createRoots(@NonNls mockSdkPath: String): List<VirtualFile> {
//        val localFS = LocalFileSystem.getInstance()
//        return mutableListOf<VirtualFile>().apply {
//            addIfNotNull(localFS.refreshAndFindFileByIoFile(File(mockSdkPath, "Lib")))
//            addIfNotNull(localFS.refreshAndFindFileByIoFile(File(mockSdkPath, PythonSdkUtil.SKELETON_DIR_NAME)))
//            addIfNotNull(PyUserSkeletonsUtil.getUserSkeletonsDirectory())
//        }
//    }
//
//    private fun toVersionString(level: LanguageLevel): String {
//        return "Python " + level.toPythonVersion()
//    }
//
//    private fun LanguageLevel.toPythonVersion(): String {
//        return "${version / 100}.${version % 100}"
//    }
//
//    private class PyMockSdkType(private val myLevel: LanguageLevel) : SdkTypeId {
//        override fun getName() = PyNames.PYTHON_SDK_ID_NAME
//        override fun getVersionString(sdk: Sdk) = toVersionString(myLevel)
//        override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}
//        override fun loadAdditionalData(currentSdk: Sdk, additional: Element): SdkAdditionalData? = null
//    }
//}
