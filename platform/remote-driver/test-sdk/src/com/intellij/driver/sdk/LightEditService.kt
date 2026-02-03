package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.intellij.ide.lightEdit.LightEditService")
interface LightEditService {
  fun getProject(): Project?
}