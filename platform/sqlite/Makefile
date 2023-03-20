VERSION := 3.41.0-jb.2

.phony: archive-native install-native

archive-native:
	rm -f target/sqlite-native.jar
	cd target && zip -r sqlite-native.jar sqlite -i '*.jnilib' '*.so' '*.dll' '*.sha256'

install-native: archive-native
	mvn install:install-file -DgroupId=org.sqlite \
      -DartifactId=native \
      -Dversion=$(VERSION) \
      -Dpackaging=jar \
      -Dfile=target/sqlite-native.jar

deploy-native: archive-native
	mvn deploy:deploy-file -DgroupId=org.sqlite \
      -DartifactId=native \
      -Dversion=$(VERSION) \
      -Dpackaging=jar \
      -Dfile=target/sqlite-native.jar \
      -DrepositoryId=space-intellij-dependencies \
      -Durl=https://packages.jetbrains.team/maven/p/ij/intellij-dependencies