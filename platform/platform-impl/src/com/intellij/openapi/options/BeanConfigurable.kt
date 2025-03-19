// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Setter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.NonNls
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.JCheckBox
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

abstract class BeanConfigurable<T : Any> protected constructor(protected val instance: T) : UnnamedConfigurable, ConfigurableWithOptionDescriptors, UiDslUnnamedConfigurable {
  var title: @NlsContexts.BorderTitle String? = null
    protected set

  private val myFields: MutableList<CheckboxField> = ArrayList<CheckboxField>()

  protected constructor(beanInstance: T, title: @NlsContexts.BorderTitle String?) : this(beanInstance) {
    this.title = title
  }

  private abstract class BeanPropertyAccessor {
    abstract fun getBeanValue(instance: Any): Boolean

    abstract fun setBeanValue(instance: Any, value: Boolean)
  }

  private class BeanFieldAccessor(private val myFieldName: String) : BeanPropertyAccessor() {
    fun getterName(): @NonNls String {
      return "is" + StringUtil.capitalize(myFieldName)
    }

    override fun getBeanValue(instance: Any): Boolean {
      try {
        val field = instance.javaClass.getField(myFieldName)
        return field.get(instance) as Boolean
      }
      catch (_: NoSuchFieldException) {
        try {
          val method = instance.javaClass.getMethod(getterName())
          return method.invoke(instance) as Boolean
        }
        catch (e1: Exception) {
          throw RuntimeException(e1)
        }
      }
      catch (e: IllegalAccessException) {
        throw RuntimeException(e)
      }
    }

    override fun setBeanValue(instance: Any, value: Boolean) {
      try {
        val field = instance.javaClass.getField(myFieldName)
        field.set(instance, value)
      }
      catch (_: NoSuchFieldException) {
        try {
          val method = instance.javaClass.getMethod("set" + StringUtil.capitalize(myFieldName), Boolean::class.java)
          method.invoke(instance, value)
        }
        catch (e1: Exception) {
          throw RuntimeException(e1)
        }
      }
      catch (e: IllegalAccessException) {
        throw RuntimeException(e)
      }
    }
  }

  private class BeanMethodAccessor(
    private val myGetter: Supplier<Boolean>,
    private val mySetter: Setter<in Boolean>,
  ) : BeanPropertyAccessor() {
    override fun getBeanValue(instance: Any): Boolean {
      return myGetter.get()
    }

    override fun setBeanValue(instance: Any, value: Boolean) {
      mySetter.set(value)
    }
  }

  private class BeanKPropertyAccessor(private val myProperty: KMutableProperty0<Boolean>) : BeanPropertyAccessor() {
    override fun getBeanValue(instance: Any): Boolean {
      return myProperty.get()
    }

    override fun setBeanValue(instance: Any, value: Boolean) {
      myProperty.set(value)
    }
  }

  private class CheckboxField {
    private val myAccessor: BeanPropertyAccessor
    val title: @NlsContexts.Checkbox String?

    constructor(fieldName: String, title: @NlsContexts.Checkbox String?) {
      myAccessor = BeanFieldAccessor(fieldName)
      this.title = title
    }

    constructor(accessor: BeanPropertyAccessor, title: @NlsContexts.Checkbox String) {
      myAccessor = accessor
      this.title = title
    }

    fun setValue(settingsInstance: Any, value: Boolean) {
      myAccessor.setBeanValue(settingsInstance, value)
    }

    fun getValue(settingsInstance: Any): Boolean {
      return myAccessor.getBeanValue(settingsInstance)
    }

    val component: JCheckBox by lazy { JCheckBox(title) }

    fun isModified(instance: Any): Boolean {
      val beanValue = myAccessor.getBeanValue(instance)
      return !Comparing.equal(componentValue, beanValue)
    }

    fun apply(instance: Any) {
      myAccessor.setBeanValue(instance, componentValue)
    }

    fun reset(instance: Any) {
      componentValue = myAccessor.getBeanValue(instance)
    }

    var componentValue: Boolean
      get() = component.isSelected
      set(value) = component.setSelected(value)
  }

  @Deprecated("use {@link #checkBox(String, Getter, Setter)} instead", level = DeprecationLevel.ERROR)
  protected fun checkBox(fieldName: @NonNls String, title: @NlsContexts.Checkbox String?) {
    myFields.add(CheckboxField(fieldName, title))
  }

  /**
   * Adds check box with given `title`.
   * Initial checkbox value is obtained from `getter`.
   * After the apply, the value from the check box is written back to model via `setter`.
   */
  protected fun checkBox(
    title: @NlsContexts.Checkbox String,
    getter: Getter<Boolean>,
    setter: Setter<in Boolean>,
  ) {
    val field = CheckboxField(BeanMethodAccessor(getter, setter), title)
    myFields.add(field)
  }

  protected fun checkBox(title: @NlsContexts.Checkbox String, prop: KMutableProperty0<Boolean>) {
    myFields.add(CheckboxField(BeanKPropertyAccessor(prop), title))
  }

  override fun getOptionDescriptors(
    configurableId: String,
    nameConverter: Function<in String?, String?>,
  ): List<OptionDescription> {
    return myFields.map {
      object : BooleanOptionDescription(nameConverter.apply(it.title), configurableId) {
        override fun isOptionEnabled(): Boolean {
          return it.getValue(instance)
        }

        override fun setOptionState(enabled: Boolean) {
          it.setValue(instance, enabled)
        }
      }
    }
  }

  override fun createComponent(): JComponent {
    return ConfigurableBuilderHelper.createBeanPanel(this, components)
  }

  override fun Panel.createContent() {
    ConfigurableBuilderHelper.integrateBeanPanel(this, this@BeanConfigurable, this@BeanConfigurable.components)
  }

  override fun isModified(): Boolean {
    for (field in myFields) {
      if (field.isModified(instance)) return true
    }
    return false
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    for (field in myFields) {
      field.apply(instance)
    }
  }

  override fun reset() {
    for (field in myFields) {
      field.reset(instance)
    }
  }

  private val components: List<JComponent>
    get() = myFields.map { it.component }
}
