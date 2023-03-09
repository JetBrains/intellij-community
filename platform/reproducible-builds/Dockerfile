FROM ubuntu:20.04
ENV LANG=C.UTF-8
ENV SHELL=/bin/bash
ENV JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto
COPY --from=amazoncorretto:17 $JAVA_HOME $JAVA_HOME
ENV PATH=$JAVA_HOME/bin:$PATH
RUN apt-get -qq update && apt-get install -y \
    git \
    tar \
    unzip \
    p7zip-full \
    squashfs-tools  \
    && rm -rf /var/lib/apt/lists/*
RUN javap -version && \
    git --version && \
    tar --version && \
    which unzip \
    which 7z && \
    which unsquashfs
