struct DummyStruct {
    int myInteger;
    short myShort;
    long myLong;
    float myFloat;
    double myDouble;
};

struct OtherStruct {
    struct DummyStruct dummy1;
    struct DummyStruct dummy2;
};

struct DummyStruct* createDummyStruct();

struct OtherStruct createOtherStruct();
