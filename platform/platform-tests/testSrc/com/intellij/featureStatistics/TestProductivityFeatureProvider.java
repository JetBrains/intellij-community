package com.intellij.featureStatistics;

public class TestProductivityFeatureProvider extends ProductivityFeaturesProvider {
  static final String tipId = "testTip";
  static final String groupId = "testGroup";
  private boolean asked;

  public TestProductivityFeatureProvider() {
    int i = 0;
  }

  @Override
  public FeatureDescriptor[] getFeatureDescriptors() {
    asked = true;
    return new FeatureDescriptor[] {
      new FeatureDescriptor(tipId, groupId, "TestTip", "test", 0, 0, null, 0, this)
    };
  }

  @Override
  public GroupDescriptor[] getGroupDescriptors() {
    return new GroupDescriptor[]{
      new GroupDescriptor(groupId, "test")
    };
  }

  @Override
  public String toString() {
    return super.toString() + "; was asked: "+asked;
  }
}
