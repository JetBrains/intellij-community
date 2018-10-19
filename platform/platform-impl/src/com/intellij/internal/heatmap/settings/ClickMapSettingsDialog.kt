// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.heatmap.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.SERVICE_NAME_PREFIX
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.heatmap.actions.ShareType
import com.intellij.internal.heatmap.fus.DEFAULT_SERVICE_URLS
import com.intellij.internal.heatmap.fus.getBuildsForIDEVersions
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import org.jdesktop.swingx.calendar.DateUtils
import java.awt.Color
import java.text.DateFormat
import java.text.ParseException
import java.util.*
import javax.swing.JComponent


private const val SERVICE_URL_KEY = "com.intellij.plugin.heatmap.settings.service.urls"
private const val SHARE_TYPE_KEY = "com.intellij.plugin.heatmap.settings.share.type"
private const val START_DATE_KEY = "com.intellij.plugin.heatmap.settings.start.date"
private const val END_DATE_KEY = "com.intellij.plugin.heatmap.settings.end.date"
private const val REMEMBER_TOKEN_KEY = "com.intellij.plugin.heatmap.settings.token.remember"
private const val FILTER_VERSIONS_KEY = "com.intellij.plugin.heatmap.settings.versions.do.filter"
private const val SELECTED_VERSIONS_KEY = "com.intellij.plugin.heatmap.settings.versions.selected"
private const val INCLUDE_EAP_KEY = "com.intellij.plugin.heatmap.settings.eap.include.selected"
private const val COLOR_KEY = "com.intellij.plugin.heatmap.settings.heatmap.color"

val DEFAULT_START_DATE = DateUtils.addDays(Date().time, -14)

class ClickMapSettingsDialog(myProject: Project, private var myDialogForm: ClickMapDialogForm = ClickMapDialogForm()) :
  DialogWrapper(myProject, myDialogForm.myJPanel, true, DialogWrapper.IdeModalityType.IDE) {

  companion object {
    private var myServiceUrl: String? = null
    private var myShareType = ShareType.BY_PLACE
    private lateinit var myStartDate: Date
    private lateinit var myEndDate: Date
    private var myAccessToken: String? = null
    private val myIDEVersions = mutableListOf<String>()
    private lateinit var myColor: Color
  }

  fun getServiceUrl() = myServiceUrl
  fun getShareType() = myShareType
  fun getStartEndDate() = Pair(myStartDate, myEndDate)
  fun getAccessToken() = myAccessToken
  fun getBuilds(): List<String> {
    return if (myDialogForm.filterVersions()) {
      getBuildsForIDEVersions(myIDEVersions, myDialogForm.myIncludeEAPSelected)
    }
    else emptyList()
  }

  fun getIncludeEap(): Boolean = myDialogForm.myIncludeEAPSelected

  fun getColor(): Color = myColor


  init {
    val props = PropertiesComponent.getInstance()

    //load URL
    val serviceUrl = props.getValue(SERVICE_URL_KEY)
    if (serviceUrl != null) {
      myServiceUrl = serviceUrl
    }
    else myServiceUrl = DEFAULT_SERVICE_URLS.first()

    //load ShareType
    val shareType = props.getValue(SHARE_TYPE_KEY, "BY_PLACE")
    myShareType = try {
      ShareType.valueOf(shareType)
    }
    catch (e: Exception) {
      ShareType.BY_PLACE
    }

    //load start/and date
    val startDateString = props.getValue(START_DATE_KEY, DEFAULT_START_DATE.toString())
    val endDateString = props.getValue(END_DATE_KEY, Date().toString())
    myStartDate = try {
      DateFormat.getInstance().parse(startDateString)
    }
    catch (e: ParseException) {
      Date(DEFAULT_START_DATE)
    }
    myEndDate = try {
      DateFormat.getInstance().parse(endDateString)
    }
    catch (e: ParseException) {
      Date()
    }
    myDialogForm.setStartDate(myStartDate)
    myDialogForm.setEndDate(myEndDate)
    myDialogForm.setServiceUrls(DEFAULT_SERVICE_URLS.union(arrayListOf(myServiceUrl)).toList())
    myDialogForm.setSelectedItem(myServiceUrl)
    myDialogForm.shareType = myShareType

    //load versions filter 
    val filterVersions = props.getBoolean(FILTER_VERSIONS_KEY, false)
    myDialogForm.myFilterVersionsSelected = filterVersions
    val isIncludeEap = props.getBoolean(INCLUDE_EAP_KEY, false)
    myDialogForm.myIncludeEAPSelected = isIncludeEap
    val selectedVersions = props.getValues(SELECTED_VERSIONS_KEY)
    myIDEVersions.clear()
    if (selectedVersions != null) {
      for (i in 0 until selectedVersions.size) {
        val version = selectedVersions[i]?.trim()
        if (version != null) myIDEVersions.add(version)
      }
    }
    myDialogForm.selectedVersions = myIDEVersions

    //load remember token
    val rememberPassword = props.getBoolean(REMEMBER_TOKEN_KEY, false)
    myDialogForm.setRememberTokenSelected(rememberPassword)

    //load password
    val attributes: CredentialAttributes = createAttributes(rememberPassword.not())
    val credentials = PasswordSafe.instance.get(attributes)
    val token = credentials?.getPasswordAsString()
    if (token != null) myAccessToken = token
    myDialogForm.myAccessTokenField.text = myAccessToken

    //load color
    val rgb: Int = try {
      props.getValue(COLOR_KEY)?.toInt() ?: Color.RED.rgb
    }
    catch (e: Exception) {
      Color.RED.rgb
    }
    myDialogForm.selectedColor = Color(rgb)

    init()
    title = "IDE Clicks Map Settings"
  }

  override fun getPreferredFocusedComponent(): JComponent = myDialogForm.preferredFocusComponent

  override fun doOKAction() {
    myIDEVersions.clear()
    myServiceUrl = null

    myAccessToken = String(myDialogForm.myAccessTokenField.password)
    myStartDate = myDialogForm.myStartDatePicker.date
    myEndDate = myDialogForm.myEndDatePicker.date
    myShareType = myDialogForm.shareType

    val versions = myDialogForm.selectedVersions
    myIDEVersions.addAll(versions)

    val url = myDialogForm.serviceUrl
    myServiceUrl = url
    myColor = myDialogForm.selectedColor
    super.doOKAction()

    //save values
    val props = PropertiesComponent.getInstance()
    props.setValue(SERVICE_URL_KEY, myServiceUrl)
    val df = DateFormat.getInstance()
    props.setValue(START_DATE_KEY, df.format(myStartDate))
    props.setValue(END_DATE_KEY, df.format(myEndDate))
    props.setValue(SHARE_TYPE_KEY, myShareType.name)
    val rememberPassword = myDialogForm.myRememberTokenSelected
    props.setValue(REMEMBER_TOKEN_KEY, rememberPassword, false)

    //save versions filter
    val filterVersions = myDialogForm.myFilterVersionsSelected
    props.setValue(FILTER_VERSIONS_KEY, filterVersions)
    val isIncludeEap = myDialogForm.myIncludeEAPSelected
    props.setValues(SELECTED_VERSIONS_KEY, myIDEVersions.toTypedArray())
    props.setValue(INCLUDE_EAP_KEY, isIncludeEap, isIncludeEap.not())

    //save selected color
    props.setValue(COLOR_KEY, myColor.rgb.toString())

    //save password
    val attributes: CredentialAttributes = createAttributes(rememberPassword.not())
    val credentials = Credentials(null, myAccessToken)
    PasswordSafe.instance[attributes, credentials] = rememberPassword.not()
  }

  private fun createAttributes(memoryOnly: Boolean): CredentialAttributes {
    val serviceName = "$SERVICE_NAME_PREFIX IDE Heat Map - Analytics"
    return CredentialAttributes(serviceName, null, null, memoryOnly)
  }

  override fun createCenterPanel(): JComponent? {
    return myDialogForm.myJPanel
  }

}