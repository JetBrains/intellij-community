// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Setter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.util.containers.ContainerUtil;
import kotlin.reflect.KMutableProperty0;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class BeanConfigurable<T> implements UnnamedConfigurable, ConfigurableWithOptionDescriptors, UiDslUnnamedConfigurable {

  private final @NotNull T myInstance;
  private @NlsContexts.BorderTitle @Nullable String myTitle;

  private final List<CheckboxField> myFields = new ArrayList<>();

  protected BeanConfigurable(@NotNull T beanInstance) {
    myInstance = beanInstance;
  }

  protected BeanConfigurable(@NotNull T beanInstance, @NlsContexts.BorderTitle @Nullable String title) {
    this(beanInstance);
    setTitle(title);
  }

  private abstract static class BeanPropertyAccessor {
    abstract @NotNull Boolean getBeanValue(@NotNull Object instance);

    abstract void setBeanValue(@NotNull Object instance, @NotNull Boolean value);
  }

  private static final class BeanFieldAccessor extends BeanPropertyAccessor {
    private final @NotNull String myFieldName;

    private BeanFieldAccessor(@NotNull String fieldName) {
      myFieldName = fieldName;
    }

    private @NonNls String getterName() {
      return "is" + StringUtil.capitalize(myFieldName);
    }

    @Override
    @NotNull Boolean getBeanValue(@NotNull Object instance) {
      try {
        Field field = instance.getClass().getField(myFieldName);
        return (Boolean)field.get(instance);
      }
      catch (NoSuchFieldException e) {
        try {
          final Method method = instance.getClass().getMethod(getterName());
          return (Boolean)method.invoke(instance);
        }
        catch (Exception e1) {
          throw new RuntimeException(e1);
        }
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    void setBeanValue(@NotNull Object instance, @NotNull Boolean value) {
      try {
        Field field = instance.getClass().getField(myFieldName);
        field.set(instance, value);
      }
      catch (NoSuchFieldException e) {
        try {
          final Method method = instance.getClass().getMethod("set" + StringUtil.capitalize(myFieldName), boolean.class);
          method.invoke(instance, value);
        }
        catch (Exception e1) {
          throw new RuntimeException(e1);
        }
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static final class BeanMethodAccessor extends BeanPropertyAccessor {
    private final @NotNull Supplier<Boolean> myGetter;
    private final @NotNull Setter<? super Boolean> mySetter;

    private BeanMethodAccessor(@NotNull Supplier<Boolean> getter, @NotNull Setter<? super Boolean> setter) {
      myGetter = getter;
      mySetter = setter;
    }

    @Override
    @NotNull Boolean getBeanValue(@NotNull Object instance) {
      return myGetter.get();
    }

    @Override
    void setBeanValue(@NotNull Object instance, @NotNull Boolean value) {
      //noinspection unchecked
      mySetter.set(value);
    }
  }

  private static final class BeanKPropertyAccessor extends BeanPropertyAccessor {
    private final @NotNull KMutableProperty0<Boolean> myProperty;

    private BeanKPropertyAccessor(@NotNull KMutableProperty0<Boolean> property) {
      myProperty = property;
    }

    @Override
    @NotNull Boolean getBeanValue(@NotNull Object instance) {
      return myProperty.get();
    }

    @Override
    void setBeanValue(@NotNull Object instance, @NotNull Boolean value) {
      //noinspection unchecked
      myProperty.set(value);
    }
  }

  private static class CheckboxField {

    private final @NotNull BeanPropertyAccessor myAccessor;
    private final @NlsContexts.Checkbox String myTitle;
    private @Nullable JCheckBox myComponent;

    private CheckboxField(String fieldName, @NlsContexts.Checkbox String title) {
      myAccessor = new BeanFieldAccessor(fieldName);
      myTitle = title;
    }

    private CheckboxField(@NotNull BeanPropertyAccessor accessor, @NlsContexts.Checkbox @NotNull String title) {
      myAccessor = accessor;
      myTitle = title;
    }

    private String getTitle() {
      return myTitle;
    }

    private void setValue(Object settingsInstance, boolean value) {
      myAccessor.setBeanValue(settingsInstance, value);
    }

    private boolean getValue(Object settingsInstance) {
      return myAccessor.getBeanValue(settingsInstance);
    }

    private @NotNull JCheckBox getComponent() {
      if (myComponent == null) {
        myComponent = new JCheckBox(myTitle);
      }
      return myComponent;
    }

    boolean isModified(@NotNull Object instance) {
      Object beanValue = myAccessor.getBeanValue(instance);
      return !Comparing.equal(getComponentValue(), beanValue);
    }

    void apply(@NotNull Object instance) {
      myAccessor.setBeanValue(instance, getComponentValue());
    }

    void reset(@NotNull Object instance) {
      setComponentValue(myAccessor.getBeanValue(instance));
    }

    @NotNull Boolean getComponentValue() {
      return getComponent().isSelected();
    }

    void setComponentValue(@NotNull Boolean value) {
      getComponent().setSelected(value);
    }
  }

  public @Nullable String getTitle() {
    return myTitle;
  }

  protected void setTitle(@NlsContexts.BorderTitle @Nullable String title) {
    myTitle = title;
  }

  protected @NotNull T getInstance() {
    return myInstance;
  }

  /**
   * @deprecated use {@link #checkBox(String, Getter, Setter)} instead
   */
  @Deprecated(forRemoval = true)
  protected void checkBox(@NonNls String fieldName, @NlsContexts.Checkbox String title) {
    myFields.add(new CheckboxField(fieldName, title));
  }

  /**
   * Adds check box with given {@code title}.
   * Initial checkbox value is obtained from {@code getter}.
   * After the apply, the value from the check box is written back to model via {@code setter}.
   */
  protected void checkBox(@NlsContexts.Checkbox @NotNull String title,
                          @NotNull Getter<Boolean> getter,
                          @NotNull Setter<? super Boolean> setter) {
    CheckboxField field = new CheckboxField(new BeanMethodAccessor(getter, setter), title);
    myFields.add(field);
  }

  protected void checkBox(@NlsContexts.Checkbox @NotNull String title, @NotNull KMutableProperty0<Boolean> prop) {
    myFields.add(new CheckboxField(new BeanKPropertyAccessor(prop), title));
  }

  @Override
  public @Unmodifiable @NotNull List<OptionDescription> getOptionDescriptors(@NotNull String configurableId,
                                                                             @NotNull Function<? super String, @Nls String> nameConverter) {
    return ContainerUtil.map(myFields, box -> new BooleanOptionDescription(nameConverter.apply(box.getTitle()), configurableId) {
      @Override
      public boolean isOptionEnabled() {
        return box.getValue(myInstance);
      }

      @Override
      public void setOptionState(boolean enabled) {
        box.setValue(myInstance, enabled);
      }
    });
  }

  @Override
  public JComponent createComponent() {
    return ConfigurableBuilderHelper.createBeanPanel(this, getComponents());
  }

  @Override
  public void createContent(@NotNull Panel rootPanel) {
    ConfigurableBuilderHelper.integrateBeanPanel(rootPanel, this, getComponents());
  }

  @Override
  public boolean isModified() {
    for (CheckboxField field : myFields) {
      if (field.isModified(myInstance)) return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (CheckboxField field : myFields) {
      field.apply(myInstance);
    }
  }

  @Override
  public void reset() {
    for (CheckboxField field : myFields) {
      field.reset(myInstance);
    }
  }

  private @Unmodifiable List<JComponent> getComponents() {
    return ContainerUtil.map(myFields, field -> field.getComponent());
  }
}
