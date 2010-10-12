package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxQualifiedName {
  private final List<String> myComponents;

  public JavaFxQualifiedName(final int size) {
    myComponents = new ArrayList<String>(size);
  }

  public JavaFxQualifiedName(final String... components) {
    this(components.length);
    ContainerUtil.addAll(myComponents, components);
  }

  public JavaFxQualifiedName(final Collection<String> components) {
    this(components.size());
    myComponents.addAll(components);
  }

  public JavaFxQualifiedName append(String name) {
    final JavaFxQualifiedName result = new JavaFxQualifiedName(myComponents.size()+1);
    result.myComponents.addAll(myComponents);
    result.myComponents.add(name);
    return result;
  }

  @NotNull
  public JavaFxQualifiedName removeLastComponent() {
    final int size = myComponents.size();
    final JavaFxQualifiedName result = new JavaFxQualifiedName(size);
    result.myComponents.addAll(myComponents);
    result.myComponents.remove(size-1);
    return result;
  }

  public int getComponentCount() {
    return myComponents.size();
  }

  public List<String> getComponents() {
    return myComponents;
  }

  @Nullable
  public String getLastComponent() {
    if (myComponents.size() == 0) {
      return null;
    }
    return myComponents.get(myComponents.size() - 1);
  }

  public static JavaFxQualifiedName fromString(final String s) {
    return new JavaFxQualifiedName(StringUtil.split(s, "."));
  }

  public static void serialize(@Nullable final JavaFxQualifiedName qName, final StubOutputStream dataStream) throws IOException {
    if (qName == null) {
      dataStream.writeVarInt(0);
    }
    else {
      dataStream.writeVarInt(qName.getComponentCount());
      for (String s : qName.myComponents) {
        dataStream.writeName(s);
      }
    }
  }

  @Nullable
  public static JavaFxQualifiedName deserialize(final StubInputStream dataStream) throws IOException {
    final JavaFxQualifiedName qName;
    final int size = dataStream.readVarInt();
    if (size == 0) {
      qName = null;
    }
    else {
      qName = new JavaFxQualifiedName(size);
      for (int i = 0; i < size; i++) {
        final StringRef name = dataStream.readName();
        qName.myComponents.add(name == null ? null : name.getString());
      }
    }
    return qName;
  }

  @Override
  public String toString() {
    return StringUtil.join(myComponents, ".");
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final JavaFxQualifiedName that = (JavaFxQualifiedName)o;
    return myComponents.equals(that.myComponents);
  }

  @Override
  public int hashCode() {
    return myComponents.hashCode();
  }
}
