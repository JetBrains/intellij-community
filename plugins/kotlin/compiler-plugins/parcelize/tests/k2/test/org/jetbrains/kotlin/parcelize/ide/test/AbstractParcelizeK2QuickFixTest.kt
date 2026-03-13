// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.parcelize.ide.test

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.k2.quickfix.tests.AbstractK2QuickFixTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.parcelize.ParcelizeNames
import org.jetbrains.kotlin.parcelize.fir.FirParcelizeExtensionRegistrar

private val WITH_PARCELIZE_KOTLIN_TEST = KotlinWithJdkAndRuntimeLightProjectDescriptor(
    arrayListOf(
        TestKotlinArtifacts.kotlinStdlib,
        TestKotlinArtifacts.parcelizeRuntime
    ),
    arrayListOf(TestKotlinArtifacts.kotlinStdlibSources)
)

abstract class AbstractParcelizeK2QuickFixTest : AbstractK2QuickFixTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = WITH_PARCELIZE_KOTLIN_TEST

    override fun setUp() {
        super.setUp()
        myFixture.addClass("""package android.os; 
            public final class Parcel {
                public int readInt() { throw new RuntimeException("Stub!"); }
                public String readString() { throw new RuntimeException("Stub!"); }
                public void writeInt(int val) { throw new RuntimeException("Stub!"); }
                public void writeString(String val) { throw new RuntimeException("Stub!"); }
            }""")
        myFixture.addClass(
            """package android.os; 
               public interface Parcelable {
                    int CONTENTS_FILE_DESCRIPTOR = 1;
                    int PARCELABLE_WRITE_RETURN_VALUE = 1;
                    int describeContents();
                    void writeToParcel(Parcel var1, int var2);
                    interface ClassLoaderCreator<T> extends Creator<T> {
                        T createFromParcel(Parcel var1, ClassLoader var2);
                    }
                    interface Creator<T> {
                        T createFromParcel(Parcel var1);
                        T[] newArray(int var1);
                    }
               }"""
        )
        val extensionPoint = project.extensionArea.getExtensionPoint<FirExtensionRegistrarAdapter>(FirExtensionRegistrarAdapter.name)
        extensionPoint.registerExtension(
            FirParcelizeExtensionRegistrar(ParcelizeNames.PARCELIZE_CLASS_FQ_NAMES),
            testRootDisposable
        )
    }
}
