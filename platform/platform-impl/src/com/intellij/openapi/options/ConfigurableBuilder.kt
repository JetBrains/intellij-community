// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Setter
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.JCheckBox

@Deprecated("Use identical {@link BeanConfigurable} for replacement")
@ApiStatus.ScheduledForRemoval
abstract class ConfigurableBuilder
@Deprecated("Use BeanConfigurable", level = DeprecationLevel.HIDDEN)
protected constructor() : UiDslUnnamedConfigurable.Simple(), UiDslUnnamedConfigurable, ConfigurableWithOptionDescriptors {

  @ApiStatus.Internal
  internal class CallbackAccessor(private val myGetter: Supplier<Boolean>, private val mySetter: Setter<in Boolean?>) {

    var value: Boolean
      get() = myGetter.get()
      set(value) = mySetter.set(value)
  }

  @ApiStatus.Internal
  internal class BeanField(
    private val myAccessor: CallbackAccessor,
    internal val title: @NlsContexts.Checkbox String,
  ) {

    val component: JCheckBox by lazy { JCheckBox(this.title) }

    fun isModified(): Boolean {
      val componentValue = this.componentValue
      val beanValue = myAccessor.value
      return !Comparing.equal<Any?>(componentValue, beanValue)
    }

    fun apply() {
      myAccessor.value = this.componentValue
    }

    fun reset() {
      this.componentValue = myAccessor.value
    }

    private var componentValue: Boolean
      get() = component.isSelected
      set(value) = component.setSelected(value)

    internal var accessorValue: Boolean
      get() = myAccessor.value
      set(value) {
        myAccessor.value = value
      }
  }

  private val myFields = mutableListOf<BeanField>()

  /**
   * Adds check box with given `title`.
   * Initial checkbox value is obtained from `getter`.
   * After the apply, the value from the check box is written back to model via `setter`.
   */
  protected open fun checkBox(@NlsContexts.Checkbox title: @NlsContexts.Checkbox String, getter: Getter<Boolean>, setter: Setter<in Boolean?>) {
    myFields.add(BeanField(CallbackAccessor(getter, setter), title))
  }

  override fun getOptionDescriptors(
    configurableId: String,
    nameConverter: Function<in String?, String?>,
  ): List<OptionDescription> {
    return myFields.map { box ->
      object : BooleanOptionDescription(nameConverter.apply(box.title), configurableId) {
        override fun isOptionEnabled(): Boolean {
          return box.accessorValue
        }

        override fun setOptionState(enabled: Boolean) {
          box.accessorValue = enabled
        }
      }
    }
  }

  override fun Panel.createContent() {
    for (field in myFields) {
      row {
        cell(field.component)
          .onApply { field.apply() }
          .onIsModified { field.isModified() }
          .onReset { field.reset() }
        UIUtil.applyDeprecatedBackground(field.component)
      }
    }
  }

  @ApiStatus.Internal
  companion object {

    @ApiStatus.Internal
    @JvmStatic
    fun getConfigurableTitle(configurable: UnnamedConfigurable): String? {
      if (configurable is BeanConfigurable<*>) {
        return configurable.title
      }
      if (configurable is BoundConfigurable) {
        return configurable.displayName
      }
      return null
    }
  }
}
