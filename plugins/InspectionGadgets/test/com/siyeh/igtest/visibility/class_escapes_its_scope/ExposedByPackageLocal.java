import java.util.List;

public class ExposedByPackageLocal {
    public static class NestedPublic { }
    protected static class NestedProtected { }
    static class NestedPackageLocal { }
    private static class NestedPrivate { }

    public NestedPublic withPublic1(
      List<NestedPublic> list) { return list.get(0);}
    protected NestedPublic withPublic2(
      List<NestedPublic> list) { return list.get(0);}
    NestedPublic withPublic3(
      List<NestedPublic> list) { return list.get(0);}
    private NestedPublic withPublic4(
      List<NestedPublic> list) { return list.get(0);}

    public NestedProtected withProtected1(
      List<NestedProtected> list) { return list.get(0);}
    protected NestedProtected withProtected2(
      List<NestedProtected> list) { return list.get(0);}
    NestedProtected withProtected3(
      List<NestedProtected> list) { return list.get(0);}
    private NestedProtected withProtected4(
      List<NestedProtected> list) { return list.get(0);}

    public NestedPackageLocal withPackageLocal1(
      List<NestedPackageLocal> list) { return list.get(0);}
    protected NestedPackageLocal withPackageLocal2(
      List<NestedPackageLocal> list) { return list.get(0);}
    NestedPackageLocal withPackageLocal3(
      List<NestedPackageLocal> list) { return list.get(0);}
    private NestedPackageLocal withPackageLocal4(
      List<NestedPackageLocal> list) { return list.get(0);}

    public NestedPrivate withPrivate1(
      List<NestedPrivate> list) { return list.get(0);}
    protected NestedPrivate withPrivate2(
      List<NestedPrivate> list) { return list.get(0);}
    <warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning> withPrivate3(
      List<<warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning>> list) { return list.get(0);}
    private NestedPrivate withPrivate4(
      List<NestedPrivate> list) { return list.get(0);}
}