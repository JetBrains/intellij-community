import abc
from typing import List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase


class EmptyFileFilterBase(ProcessorBase):
    @staticmethod
    def _filter(dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp if dp.content.strip() else None

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return False

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return []


class EmptyFileFilterTrainData(EmptyFileFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._filter(dp)


class EmptyFileFilterTrainModel(EmptyFileFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._filter(dp)


class EmptyFileFilterInference(EmptyFileFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp
