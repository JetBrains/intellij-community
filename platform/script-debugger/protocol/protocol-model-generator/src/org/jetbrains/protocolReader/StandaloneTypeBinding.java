package org.jetbrains.protocolReader;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jsonProtocol.ProtocolMetaModel;

import java.io.IOException;
import java.util.List;

interface StandaloneTypeBinding {
  BoxableType getJavaType();

  void generate() throws IOException;

  /**
   * @return null if not direction-specific
   */
  @Nullable
  TypeData.Direction getDirection();

  interface Target {
    BoxableType resolve(ResolveContext context);

    interface ResolveContext {
      BoxableType generateNestedObject(String shortName, String description, List<ProtocolMetaModel.ObjectProperty> properties);
    }
  }

  class PredefinedTarget implements Target {
    private final BoxableType resolvedType;

    PredefinedTarget(BoxableType resolvedType) {
      this.resolvedType = resolvedType;
    }

    @Override
    public BoxableType resolve(ResolveContext context) {
      return resolvedType;
    }

    public static final PredefinedTarget STRING = new PredefinedTarget(BoxableType.STRING);
    public static final PredefinedTarget INT = new PredefinedTarget(BoxableType.INT);
    public static final PredefinedTarget NUMBER = new PredefinedTarget(BoxableType.NUMBER);
    public static final PredefinedTarget MAP = new PredefinedTarget(BoxableType.MAP);
  }
}
