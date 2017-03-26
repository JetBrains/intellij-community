import java.util.List;

public class ExposedByPublic {
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

    public <warning descr="Class 'NestedProtected' is exposed outside its defined scope">NestedProtected</warning> withProtected1(
      List<<warning descr="Class 'NestedProtected' is exposed outside its defined scope">NestedProtected</warning>> list) { return list.get(0);}
    protected NestedProtected withProtected2(
      List<NestedProtected> list) { return list.get(0);}
    NestedProtected withProtected3(
      List<NestedProtected> list) { return list.get(0);}
    private NestedProtected withProtected4(
      List<NestedProtected> list) { return list.get(0);}

    public <warning descr="Class 'NestedPackageLocal' is exposed outside its defined scope">NestedPackageLocal</warning> withPackageLocal1(
      List<<warning descr="Class 'NestedPackageLocal' is exposed outside its defined scope">NestedPackageLocal</warning>> list) { return list.get(0);}
    protected <warning descr="Class 'NestedPackageLocal' is exposed outside its defined scope">NestedPackageLocal</warning> withPackageLocal2(
      List<<warning descr="Class 'NestedPackageLocal' is exposed outside its defined scope">NestedPackageLocal</warning>> list) { return list.get(0);}
    NestedPackageLocal withPackageLocal3(
      List<NestedPackageLocal> list) { return list.get(0);}
    private NestedPackageLocal withPackageLocal4(
      List<NestedPackageLocal> list) { return list.get(0);}

    public <warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning> withPrivate1(
      List<<warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning>> list) { return list.get(0);}
    protected <warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning> withPrivate2(
      List<<warning descr="Class 'NestedPrivate' is exposed outside its defined scope">NestedPrivate</warning>> list) { return list.get(0);}
    NestedPrivate withPrivate3(
      List<NestedPrivate> list) { return list.get(0);}
    private NestedPrivate withPrivate4(
      List<NestedPrivate> list) { return list.get(0);}
}