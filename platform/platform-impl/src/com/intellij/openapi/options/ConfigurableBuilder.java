// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Setter;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @deprecated Use identical {@link BeanConfigurable} for replacement
 */
@Deprecated(forRemoval = true)
public abstract class ConfigurableBuilder extends UiDslUnnamedConfigurable.Simple
  implements UiDslUnnamedConfigurable, ConfigurableWithOptionDescriptors {

  private static final class CallbackAccessor {
    private final @NotNull Supplier<Boolean> myGetter;
    private final @NotNull Setter<? super Boolean> mySetter;

    private CallbackAccessor(@NotNull Supplier<Boolean> getter, @NotNull Setter<? super Boolean> setter) {
      myGetter = getter;
      mySetter = setter;
    }

    public Boolean getValue() {
      return myGetter.get();
    }

    public void setValue(@NotNull Boolean value) {
      mySetter.set(value);
    }
  }

  @ApiStatus.Internal
  static class BeanField {

    private final @NotNull CallbackAccessor myAccessor;
    private final @NotNull @NlsContexts.Checkbox String myTitle;
    private @Nullable JCheckBox myComponent;

    private BeanField(@NotNull CallbackAccessor accessor, @NotNull @NlsContexts.Checkbox String title) {
      myAccessor = accessor;
      myTitle = title;
    }

    @NotNull
    JCheckBox getComponent() {
      if (myComponent == null) {
        myComponent = createComponent();
      }
      return myComponent;
    }

    private @NotNull JCheckBox createComponent() {
      return new JCheckBox(myTitle);
    }

    boolean isModified() {
      final Object componentValue = getComponentValue();
      final Object beanValue = myAccessor.getValue();
      return !Comparing.equal(componentValue, beanValue);
    }

    void apply() {
      myAccessor.setValue(getComponentValue());
    }

    void reset() {
      setComponentValue(myAccessor.getValue());
    }

    private Boolean getComponentValue() {
      return getComponent().isSelected();
    }

    private void setComponentValue(@NotNull Boolean value) {
      getComponent().setSelected(value);
    }

    private @NotNull String getTitle() {
      return myTitle;
    }

    private void setAccessorValue(boolean value) {
      myAccessor.setValue(value);
    }

    private boolean getAccessorValue() {
      return myAccessor.getValue();
    }
  }

  private final List<BeanField> myFields = new ArrayList<>();

  protected ConfigurableBuilder() {
  }

  /**
   * Adds check box with given {@code title}.
   * Initial checkbox value is obtained from {@code getter}.
   * After the apply, the value from the check box is written back to model via {@code setter}.
   */
  protected void checkBox(@NotNull @NlsContexts.Checkbox String title, @NotNull Getter<@NotNull Boolean> getter, @NotNull Setter<? super Boolean> setter) {
    myFields.add(new BeanField(new CallbackAccessor(getter, setter), title));
  }

  @Override
  public @Unmodifiable @NotNull List<OptionDescription> getOptionDescriptors(@NotNull String configurableId,
                                                                             @NotNull Function<? super String, @NlsContexts.Command String> nameConverter) {
    return ContainerUtil.map(myFields, box -> new BooleanOptionDescription(nameConverter.apply(box.getTitle()), configurableId) {
      @Override
      public boolean isOptionEnabled() {
        return box.getAccessorValue();
      }

      @Override
      public void setOptionState(boolean enabled) {
        box.setAccessorValue(enabled);
      }
    });
  }

  @Override
  public void createContent(@NotNull Panel builder) {
    ConfigurableBuilderHelper.buildFieldsPanel(builder, myFields);
  }

  @ApiStatus.Internal
  public static @Nullable String getConfigurableTitle(@NotNull UnnamedConfigurable configurable) {
    if (configurable instanceof BeanConfigurable<?>) {
      return ((BeanConfigurable<?>)configurable).getTitle();
    }
    return null;
  }
}
